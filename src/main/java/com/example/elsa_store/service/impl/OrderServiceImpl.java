package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderQuoteResponse;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.OrderNotificationService;
import com.example.elsa_store.service.OrderService;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Đơn hàng. Khách (USER) tự đặt đơn cho một cửa hàng; chủ, quản lý và nhân viên lập đơn tại quầy cho cửa hàng của
 * mình (kèm khách hàng nếu có). Mỗi đơn thuộc đúng một cửa hàng. Xoá đơn: chỉ chủ và quản lý. ADMIN chỉ xem.
 *
 * <p>Giá luôn tính lại ở máy chủ ({@link PricingService}); tồn kho trừ lúc đặt và hoàn lại khi đơn bị huỷ
 * ({@link StockService}). Trạng thái chỉ đi theo {@link #NEXT}: chờ xác nhận → đã xác nhận → (đang giao) → hoàn thành,
 * huỷ được khi chưa hoàn thành. Mỗi lần đổi để lại một mốc trong {@link OrderTimeline}.
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    static final String METHOD_COD = "COD";
    static final String METHOD_VNPAY = "VNPAY";
    static final int MAX_LINES = 50;
    static final int MAX_QUANTITY_PER_LINE = 999;
    static final int MAX_REASON_LENGTH = 255;

    /** Các bước chuyển trạng thái hợp lệ. */
    static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            OrderStatus.CHUA_XAC_NHAN, EnumSet.of(OrderStatus.DA_XAC_NHAN, OrderStatus.DA_HUY),
            // Đơn bán tại quầy giao tay ngay nên được hoàn thành thẳng, không qua "đang giao".
            OrderStatus.DA_XAC_NHAN, EnumSet.of(OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH, OrderStatus.DA_HUY),
            // Giao không thành (khách không nhận, sai địa chỉ…) thì huỷ để hàng về kho.
            OrderStatus.DANG_GIAO, EnumSet.of(OrderStatus.HOAN_THANH, OrderStatus.DA_HUY),
            OrderStatus.HOAN_THANH, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.DA_HUY, EnumSet.noneOf(OrderStatus.class));

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartRepository cartRepository;
    private final OrderNotificationService orderNotificationService;
    private final AccessGuard accessGuard;
    private final UserAccountFactory accountFactory;
    private final PricingService pricingService;
    private final StockService stockService;
    private final OrderTimeline timeline;
    private final StockAlerts stockAlerts;
    private final OrderExcelExporter excelExporter;
    private final ReturnPolicy returnPolicy;
    private final CoinWallet coinWallet;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            ProductVariantRepository productVariantRepository,
            CartRepository cartRepository,
            OrderNotificationService orderNotificationService,
            AccessGuard accessGuard,
            UserAccountFactory accountFactory,
            PricingService pricingService,
            StockService stockService,
            OrderTimeline timeline,
            StockAlerts stockAlerts,
            OrderExcelExporter excelExporter,
            ReturnPolicy returnPolicy,
            CoinWallet coinWallet) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productVariantRepository = productVariantRepository;
        this.cartRepository = cartRepository;
        this.orderNotificationService = orderNotificationService;
        this.accessGuard = accessGuard;
        this.accountFactory = accountFactory;
        this.pricingService = pricingService;
        this.stockService = stockService;
        this.timeline = timeline;
        this.stockAlerts = stockAlerts;
        this.excelExporter = excelExporter;
        this.returnPolicy = returnPolicy;
        this.coinWallet = coinWallet;
    }

    // ------------------------------------------------------------------ đặt đơn

    @Override
    public OrderResponse create(OrderRequest request) {
        User current = accessGuard.currentUser();
        accessGuard.rejectAdmin(current);
        boolean counterSale = current.getRole().isStoreRole();

        Map<ProductVariant, Integer> wanted = resolveItems(request, counterSale);
        Store store = storeOf(wanted.keySet());

        Order order = new Order();
        Customer customer;
        User buyer;
        if (counterSale) {
            // Lập đơn tại quầy: phải là cửa hàng của chính người gọi.
            Store mine = accessGuard.requireStoreMember();
            if (!mine.getId().equals(store.getId())) {
                throw new AppException(ErrorCode.UN_AUTHORIZED, "Products belong to another store");
            }
            customer = request.getCustomerId() == null ? null : findCustomerForCounter(request.getCustomerId(), mine);
            buyer = customer == null ? null : customer.getUser();
            order.setHandledBy(current);
        } else {
            // Khách tự đặt: đơn luôn là của chính mình.
            if (request.getUserId() != null && !request.getUserId().equals(current.getId())) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
            if (!store.isApproved()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Store is not open");
            }
            if (store.isSuspended()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Cửa hàng đang tạm ngừng hoạt động trên Elsa Store");
            }
            rejectIfPaused(store);
            buyer = current;
            customer = accountFactory.ensureCustomerProfile(current);
        }
        applyShipping(order, request, customer, counterSale);

        PricingService.Quote quote = pricingService.quote(store, lines(wanted), request.getCouponCode());

        // Trừ kho theo thứ tự id biến thể: hai đơn chứa cùng các biến thể luôn khoá dòng tồn kho cùng một thứ tự,
        // không thể chờ nhau (deadlock).
        List<OrderItem> items = new ArrayList<>();
        List<PricingService.QuoteLine> sortedLines = new ArrayList<>(quote.lines());
        sortedLines.sort(Comparator.comparing(l -> l.variant().getId()));
        for (PricingService.QuoteLine line : sortedLines) {
            boolean reserved = stockService.reserve(line.variant(), line.quantity());
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductVariant(line.variant());
            item.setQuantity(line.quantity());
            item.setOriginalUnitPrice(line.originalUnitPrice());
            item.setUnitPrice(line.unitPrice());
            item.setDiscount(line.discount());
            item.setLineTotal(line.lineTotal());
            item.setStockReserved(reserved);
            item.setCostPrice(line.variant().getCostPrice());
            items.add(item);
        }

        order.setCode(newOrderCode());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CHUA_XAC_NHAN);
        order.setStore(store);
        order.setUser(buyer);
        order.setCustomer(customer);
        boolean online = request.getPaymentMethod() != null && request.getPaymentMethod() == 1;
        order.setPaymentMethod(online ? METHOD_VNPAY : METHOD_COD);
        order.setPaymentStatus(online ? PaymentStatus.CHO_THANH_TOAN : PaymentStatus.CHUA_THANH_TOAN);
        order.setItems(items);
        double shipping = counterSale ? 0 : PricingService.shippingFee(store, quote.total());
        int coins = !counterSale && Boolean.TRUE.equals(request.getUseCoins()) ? coinWallet.usable(buyer, quote.total()) : 0;
        order.setTotalAmount(quote.originalSubtotal());
        order.setDiscountAmount(PricingService.money(quote.totalDiscount() + coins));
        order.setShippingFee(counterSale ? null : shipping);
        order.setFinalAmount(PricingService.money(quote.total() - coins + shipping));
        order.setCouponCode(quote.couponCode());
        order.setDiscountNote(withCoins(quote.discountNote(), coins));
        order.setCoinsUsed(coins > 0 ? coins : null);

        order = orderRepository.save(order);
        coinWallet.redeem(buyer, order, coins);
        timeline.record(order, OrderStatus.CHUA_XAC_NHAN, current, null);
        stockService.recordSale(order, current);
        stockAlerts.afterSale(order);
        if (!counterSale && Boolean.TRUE.equals(request.getFromCart())) {
            removeFromCart(current.getId(), wanted.keySet());
        }
        orderNotificationService.orderPlaced(order);
        return toResponse(order, viewer(current), true);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderQuoteResponse quote(OrderRequest request) {
        User current = accessGuard.currentUser();
        accessGuard.rejectAdmin(current);
        boolean counterSale = current.getRole().isStoreRole();
        Map<ProductVariant, Integer> wanted = resolveItems(request, counterSale);
        Store store = storeOf(wanted.keySet());
        if (counterSale && !accessGuard.requireStoreMember().getId().equals(store.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Products belong to another store");
        }
        if (!counterSale) {
            rejectIfPaused(store);
        }

        PricingService.Quote quote = pricingService.quote(store, lines(wanted), request.getCouponCode());
        Map<Long, Integer> stock = stockService.levels(wanted.keySet().stream().map(ProductVariant::getId).toList());
        boolean stockProblem = false;
        List<OrderQuoteResponse.Line> lines = new ArrayList<>();
        for (PricingService.QuoteLine line : quote.lines()) {
            ProductVariant variant = line.variant();
            Integer available = stock.get(variant.getId());
            boolean insufficient = available != null && line.quantity() > available;
            stockProblem |= insufficient;
            lines.add(OrderQuoteResponse.Line.builder()
                    .productVariantId(variant.getId())
                    .productId(variant.getProduct().getId())
                    .productName(variant.getProduct().getName())
                    .color(variant.getColor())
                    .size(variant.getSize())
                    .imageUrl(variant.getImageUrl() != null ? variant.getImageUrl() : variant.getProduct().getImageUrl())
                    .quantity(line.quantity())
                    .originalUnitPrice(line.originalUnitPrice())
                    .unitPrice(line.unitPrice())
                    .discount(line.discount())
                    .lineTotal(line.lineTotal())
                    .promotionName(line.promotionName())
                    .availableStock(available)
                    .insufficientStock(insufficient)
                    .build());
        }
        double shipping = counterSale ? 0 : PricingService.shippingFee(store, quote.total());
        Double threshold = counterSale ? null : store.getFreeShippingThreshold();
        boolean chargesShipping = !counterSale && store.getShippingFee() != null && store.getShippingFee() > 0;
        Double missing = chargesShipping && threshold != null && shipping > 0
                ? PricingService.money(threshold - quote.total())
                : null;
        User coinOwner = counterSale ? null : current;
        int coinsUsable = coinWallet.usable(coinOwner, quote.total());
        int coinsUsed = Boolean.TRUE.equals(request.getUseCoins()) ? coinsUsable : 0;
        return OrderQuoteResponse.builder()
                .coinBalance(coinWallet.balance(coinOwner))
                .coinsUsable(coinsUsable)
                .coinsUsed(coinsUsed)
                .coinsReward(coinWallet.rewardFor(coinOwner, quote.total() - coinsUsed, null))
                .storeId(store.getId())
                .storeName(store.getName())
                .items(lines)
                .originalSubtotal(quote.originalSubtotal())
                .subtotal(quote.subtotal())
                .productDiscount(PricingService.money(quote.originalSubtotal() - quote.subtotal()))
                .orderDiscount(quote.orderDiscount())
                .couponCode(quote.couponCode())
                .orderPromotionName(quote.orderPromotionName())
                .totalDiscount(PricingService.money(quote.totalDiscount() + coinsUsed))
                .shippingFee(shipping)
                .freeShippingThreshold(chargesShipping ? threshold : null)
                .amountToFreeShipping(missing)
                .total(PricingService.money(quote.total() - coinsUsed + shipping))
                .discountNote(withCoins(quote.discountNote(), coinsUsed))
                .stockProblem(stockProblem)
                .build();
    }

    /** Gộp dòng trùng biến thể, kiểm tra số lượng và sản phẩm còn bán. */
    private Map<ProductVariant, Integer> resolveItems(OrderRequest request, boolean counterSale) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "items: must not be empty");
        }
        if (request.getItems().size() > MAX_LINES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Too many items in one order");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getProductVariantId() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "productVariantId: must not be null");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "quantity: must be greater than 0");
            }
            quantities.merge(itemReq.getProductVariantId(), itemReq.getQuantity(), Integer::sum);
        }
        Map<ProductVariant, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            if (entry.getValue() > MAX_QUANTITY_PER_LINE) {
                throw new AppException(ErrorCode.BAD_REQUEST, "quantity: must be at most " + MAX_QUANTITY_PER_LINE);
            }
            ProductVariant variant = productVariantRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
            if (variant.getProduct().getStore() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Product does not belong to any store");
            }
            boolean variantActive = variant.getStatus() == null || variant.getStatus() == 1;
            if (!counterSale && (!ProductServiceImpl.isPublic(variant.getProduct()) || !variantActive)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Product is not available");
            }
            result.put(variant, entry.getValue());
        }
        return result;
    }

    /** Cửa hàng tạm nghỉ không nhận đơn online; lời báo kèm lời nhắn của cửa hàng. */
    static void rejectIfPaused(Store store) {
        if (store != null && store.isPaused()) {
            String note = store.getPauseNote();
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cửa hàng đang tạm nghỉ, chưa nhận đơn mới" + (note == null ? "" : ": " + note));
        }
    }

    private static Store storeOf(Collection<ProductVariant> variants) {
        Store store = null;
        for (ProductVariant variant : variants) {
            Store itemStore = variant.getProduct().getStore();
            if (store == null) {
                store = itemStore;
            } else if (!store.getId().equals(itemStore.getId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "An order can only contain products from one store");
            }
        }
        return store;
    }

    private static List<PricingService.LineRequest> lines(Map<ProductVariant, Integer> wanted) {
        return wanted.entrySet().stream()
                .map(e -> new PricingService.LineRequest(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Người nhận và địa chỉ giao: lấy từ sổ địa chỉ ({@code addressId}) hoặc gửi trực tiếp. Bắt buộc với đơn khách đặt;
     * đơn tại quầy không cần.
     */
    private void applyShipping(Order order, OrderRequest request, Customer customer, boolean counterSale) {
        String name = trimToNull(request.getReceiverName());
        String phone = trimToNull(request.getReceiverPhone());
        String address = trimToNull(request.getShippingAddress());
        if (request.getAddressId() != null) {
            Address saved = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
            if (customer == null || !saved.getCustomer().getId().equals(customer.getId())) {
                throw new AppException(ErrorCode.UN_AUTHORIZED, "Address belongs to another customer");
            }
            order.setAddress(saved);
            name = name != null ? name : trimToNull(saved.getFullName());
            phone = phone != null ? phone : trimToNull(saved.getPhone());
            address = address != null ? address : AddressServiceImpl.fullAddress(saved);
        }
        if (!counterSale) {
            if (name == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "receiverName: must not be blank");
            }
            if (phone == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "receiverPhone: must not be blank");
            }
            if (!phone.matches("[0-9+ .()-]{8,20}")) {
                throw new AppException(ErrorCode.BAD_REQUEST, "receiverPhone: invalid value");
            }
            if (address == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "shippingAddress: must not be blank");
            }
        }
        order.setReceiverName(name);
        order.setReceiverPhone(phone);
        order.setShippingAddress(address);
        order.setNote(trimToNull(request.getNote()));
    }

    private void removeFromCart(Long userId, Collection<ProductVariant> ordered) {
        Set<Long> variantIds = new HashSet<>();
        ordered.forEach(v -> variantIds.add(v.getId()));
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().removeIf(item -> variantIds.contains(item.getProductVariant().getId()));
            cart.setTotalAmount(cart.getItems().stream()
                    .mapToDouble(i -> i.getLineTotal() == null ? 0 : i.getLineTotal())
                    .sum());
            cartRepository.save(cart);
        });
    }

    /** Mã đơn dễ đọc, không đoán được và gần như không trùng: ORD-yyMMdd-XXXXXX. */
    private String newOrderCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder("ORD-")
                    .append(CODE_DATE.format(LocalDateTime.now()))
                    .append('-');
            for (int i = 0; i < 6; i++) {
                code.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            }
            if (!orderRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_UPDATE);
    }

    // ------------------------------------------------------------------ đọc

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = find(id);
        authorize(order, Access.READ);
        return toResponse(order, viewer(accessGuard.currentUser()), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        User current = accessGuard.currentUser();
        List<Order> orders = switch (current.getRole()) {
            case ADMIN -> orderRepository.findAll();
            case USER -> orderRepository.findAllByUser_IdOrderByOrderDateDesc(current.getId());
            default -> orderRepository.findAllByStore_IdOrderByOrderDateDesc(
                    accessGuard.requireStoreMember().getId());
        };
        Viewer viewer = viewer(current);
        return orders.stream().map(o -> toResponse(o, viewer)).toList();
    }

    static final int MAX_PAGE_SIZE = 50;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(OrderStatus status, String keyword, Long storeId, int page, int size) {
        User current = accessGuard.currentUser();
        Scope scope = scope(current, storeId);
        Page<Order> result = orderRepository.search(scope.storeId(), scope.userId(), status, likePattern(keyword),
                null, null, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE)));
        Viewer viewer = viewer(current);
        return PageResponse.from(result.map(o -> toResponse(o, viewer)));
    }

    static final int MAX_EXPORT_ROWS = 5000;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(OrderStatus status, String keyword, LocalDate from, LocalDate to, Long storeId) {
        User current = accessGuard.currentUser();
        Long scopeStore;
        if (current.getRole() == Role.ADMIN) {
            scopeStore = storeId;
        } else if (current.getRole().isStoreRole()) {
            // Có SĐT, địa chỉ của khách: chỉ chủ và quản lý được tải về.
            scopeStore = accessGuard.requireStoreManager().getId();
        } else {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "from: must not be after to");
        }
        List<Order> orders = orderRepository.search(scopeStore, null, status, likePattern(keyword),
                        from == null ? null : from.atStartOfDay(),
                        to == null ? null : to.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, MAX_EXPORT_ROWS))
                .getContent();
        return excelExporter.export(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus(Long storeId) {
        Scope scope = scope(accessGuard.currentUser(), storeId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        long total = 0;
        for (Object[] row : orderRepository.countByStatus(scope.storeId(), scope.userId())) {
            if (row[0] instanceof OrderStatus status && row[1] instanceof Number count) {
                counts.put(status.name(), count.longValue());
                total += count.longValue();
            }
        }
        counts.put("ALL", total);
        return counts;
    }

    /** Phạm vi đơn người xem được thấy: một cửa hàng và/hoặc một người mua; ADMIN không truyền gì = toàn hệ thống. */
    record Scope(Long storeId, Long userId) {}

    private Scope scope(User current, Long storeId) {
        return switch (current.getRole()) {
            case ADMIN -> new Scope(storeId, null);
            case USER -> new Scope(null, current.getId());
            default -> new Scope(accessGuard.requireStoreMember().getId(), null);
        };
    }

    /** "  ORD-12 " -> "%ord-12%"; bỏ các ký tự đại diện của LIKE (%, _, \) mà người dùng gõ vào. */
    static String likePattern(String keyword) {
        String text = trimToNull(keyword);
        if (text == null) {
            return null;
        }
        String escaped = text.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "");
        return escaped.isEmpty() ? null : "%" + escaped + "%";
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllByUser(Long userId) {
        User current = accessGuard.currentUser();
        List<Order> orders = switch (current.getRole()) {
            case ADMIN -> orderRepository.findAllByUser_Id(userId);
            case USER -> {
                if (!current.getId().equals(userId)) {
                    throw new AppException(ErrorCode.UN_AUTHORIZED);
                }
                yield orderRepository.findAllByUser_IdOrderByOrderDateDesc(userId);
            }
            default -> orderRepository.findAllByStore_IdAndUser_Id(
                    accessGuard.requireStoreMember().getId(), userId);
        };
        Viewer viewer = viewer(current);
        return orders.stream().map(o -> toResponse(o, viewer)).toList();
    }

    /** Ghi chú giảm giá kèm phần xu, cắt cho vừa cột 255 ký tự. */
    static String withCoins(String discountNote, int coins) {
        if (coins <= 0) {
            return discountNote;
        }
        String part = "Dùng " + coins + " xu -" + NotificationMessages.money((double) coins);
        String note = discountNote == null || discountNote.isBlank() ? part : discountNote + "; " + part;
        return note.length() <= 255 ? note : note.substring(0, 255);
    }

    // ------------------------------------------------------------------ đổi trạng thái

    @Override
    public void delete(Long id) {
        Order order = find(id);
        accessGuard.authorize(order.getStore(), Access.MANAGER_WRITE);
        // Đơn chưa giao xong mà bị xoá: hàng chưa rời kho.
        if (order.getStatus() != OrderStatus.HOAN_THANH && order.getStatus() != OrderStatus.DA_HUY) {
            stockService.releaseOrder(order, "Xoá đơn", accessGuard.currentUser());
            coinWallet.refundRedeemed(order);
        }
        coinWallet.detach(order);
        timeline.deleteFor(order);
        orderRepository.delete(order);
    }

    @Override
    public OrderResponse updateStatus(Long orderId, OrderStatus status, String reason) {
        if (status == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "status: must not be null");
        }
        String note = trimToNull(reason);
        if (note != null && note.length() > MAX_REASON_LENGTH) {
            throw new AppException(ErrorCode.BAD_REQUEST, "reason: must be at most " + MAX_REASON_LENGTH + " characters");
        }
        Order order = find(orderId);
        User current = accessGuard.currentUser();
        if (current.getRole() == Role.USER) {
            // Khách chỉ được huỷ đơn của mình khi cửa hàng chưa xác nhận, và xác nhận đã nhận hàng khi đơn đang giao
            // (bấm lần nữa thì không đổi gì).
            boolean own = order.getUser() != null && order.getUser().getId().equals(current.getId());
            if (own && status == order.getStatus() && (status == OrderStatus.DA_HUY || status == OrderStatus.HOAN_THANH)) {
                return toResponse(order, viewer(current), true);
            }
            boolean cancel = status == OrderStatus.DA_HUY && order.getStatus() == OrderStatus.CHUA_XAC_NHAN;
            boolean received = status == OrderStatus.HOAN_THANH && order.getStatus() == OrderStatus.DANG_GIAO;
            if (!own || !(cancel || received)) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
        } else {
            authorize(order, Access.STAFF_WRITE);
        }

        OrderStatus previousStatus = order.getStatus();
        if (previousStatus == status) {
            return toResponse(order, viewer(current), true);
        }
        if (previousStatus != null && !NEXT.getOrDefault(previousStatus, Set.of()).contains(status)) {
            throw new AppException(ErrorCode.INVALID_ORDER_TRANSITION);
        }
        if (status == OrderStatus.DANG_GIAO && order.getShippingAddress() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn không có địa chỉ giao hàng");
        }
        PaymentStatus paymentStatus = order.getPaymentStatus();
        if (status == OrderStatus.HOAN_THANH && !completable(paymentStatus)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hoàn thành khi chưa thanh toán");
        }
        if (status == OrderStatus.DA_HUY && paymentStatus == PaymentStatus.DA_THANH_TOAN) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hủy đơn đã thanh toán");
        }

        order.setStatus(status);
        if (status == OrderStatus.DA_HUY) {
            order.setCancelReason(note);
            stockService.releaseOrder(order, "Huỷ đơn", current);
            coinWallet.refundRedeemed(order);
        }
        if (status == OrderStatus.HOAN_THANH) {
            order.setCompletedAt(LocalDateTime.now());
            coinWallet.earn(order);
        }
        if (status == OrderStatus.HOAN_THANH && paymentStatus == PaymentStatus.CHUA_THANH_TOAN) {
            // Đơn trả khi nhận hàng (COD) đã giao xong nghĩa là đã thu tiền.
            order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
        }
        order = orderRepository.save(order);
        timeline.record(order, status, current, note);
        orderNotificationService.statusChanged(order, previousStatus, current);
        return toResponse(order, viewer(current), true);
    }

    @Override
    public boolean autoTransition(Long orderId, OrderStatus expected, OrderStatus target, String note) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != expected
                || !NEXT.getOrDefault(expected, Set.of()).contains(target)) {
            return false;
        }
        PaymentStatus payment = order.getPaymentStatus();
        if (target == OrderStatus.DA_HUY && payment == PaymentStatus.DA_THANH_TOAN) {
            return false;
        }
        if (target == OrderStatus.HOAN_THANH && !completable(payment)) {
            return false;
        }
        order.setStatus(target);
        if (target == OrderStatus.DA_HUY) {
            order.setCancelReason(note);
            stockService.releaseOrder(order, "Huỷ đơn", null);
            coinWallet.refundRedeemed(order);
        }
        if (target == OrderStatus.HOAN_THANH) {
            order.setCompletedAt(LocalDateTime.now());
            coinWallet.earn(order);
            if (payment == PaymentStatus.CHUA_THANH_TOAN) {
                order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
            }
        }
        order = orderRepository.save(order);
        timeline.record(order, target, null, note);
        orderNotificationService.statusChanged(order, expected, null);
        return true;
    }

    /** Hoàn thành được khi đã trả, hoặc trả khi nhận hàng (COD) chưa thu; VNPay đang chờ / lỗi thì chưa. */
    private static boolean completable(PaymentStatus payment) {
        return payment == null || payment == PaymentStatus.DA_THANH_TOAN || payment == PaymentStatus.CHUA_THANH_TOAN;
    }

    @Override
    public OrderResponse updatePaymentStatus(Long orderId, PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "paymentStatus: must not be null");
        }
        Order order = find(orderId);
        authorize(order, Access.STAFF_WRITE);

        PaymentStatus previousPaymentStatus = order.getPaymentStatus();
        order.setPaymentStatus(paymentStatus);

        order = orderRepository.save(order);
        orderNotificationService.paymentChanged(order, previousPaymentStatus);
        return toResponse(order, viewer(accessGuard.currentUser()), true);
    }

    private Order find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    /** Người mua xem đơn của mình; người của cửa hàng xem/sửa đơn của cửa hàng; ADMIN chỉ xem. */
    private void authorize(Order order, Access access) {
        User current = accessGuard.currentUser();
        if (current.getRole() == Role.USER) {
            boolean own = order.getUser() != null && order.getUser().getId().equals(current.getId());
            if (!own || access != Access.READ) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
            return;
        }
        accessGuard.authorize(order.getStore(), access);
    }

    /** Khách gắn vào đơn tại quầy: khách của cửa hàng hoặc khách có tài khoản. */
    private Customer findCustomerForCounter(Long customerId, Store mine) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        boolean allowed = customer.getUser() != null
                || (customer.getStore() != null && customer.getStore().getId().equals(mine.getId()))
                || orderRepository.existsByCustomer_IdAndStore_Id(customer.getId(), mine.getId());
        if (!allowed) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Customer belongs to another store");
        }
        return customer;
    }

    // ------------------------------------------------------------------ chuyển sang DTO

    /** Ai đang xem — tính một lần cho cả danh sách, không truy vấn lại cho từng đơn. */
    record Viewer(User user, Store store) {}

    private Viewer viewer(User current) {
        return new Viewer(current, current.getRole().isStoreRole() ? accessGuard.storeOf(current) : null);
    }

    /** Các trạng thái người xem được phép chuyển đơn sang (app chỉ hiện đúng các nút này). */
    static List<OrderStatus> allowedNext(Order order, Viewer viewer) {
        OrderStatus status = order.getStatus();
        if (status == null || viewer == null || viewer.user() == null) {
            return List.of();
        }
        User user = viewer.user();
        PaymentStatus payment = order.getPaymentStatus();
        if (user.getRole() == Role.USER) {
            boolean own = order.getUser() != null && order.getUser().getId().equals(user.getId());
            if (own && status == OrderStatus.CHUA_XAC_NHAN && payment != PaymentStatus.DA_THANH_TOAN) {
                return List.of(OrderStatus.DA_HUY);
            }
            if (own && status == OrderStatus.DANG_GIAO && completable(payment)) {
                return List.of(OrderStatus.HOAN_THANH);
            }
            return List.of();
        }
        boolean ownStore = viewer.store() != null
                && viewer.store().isApproved()
                && order.getStore() != null
                && viewer.store().getId().equals(order.getStore().getId());
        if (!user.getRole().isStoreRole() || !ownStore) {
            return List.of();
        }
        List<OrderStatus> result = new ArrayList<>();
        for (OrderStatus next : List.of(
                OrderStatus.DA_XAC_NHAN, OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH, OrderStatus.DA_HUY)) {
            if (!NEXT.getOrDefault(status, Set.of()).contains(next)) {
                continue;
            }
            if (next == OrderStatus.DANG_GIAO && order.getShippingAddress() == null) {
                continue;
            }
            if (next == OrderStatus.HOAN_THANH && !completable(payment)) {
                continue;
            }
            if (next == OrderStatus.DA_HUY && payment == PaymentStatus.DA_THANH_TOAN) {
                continue;
            }
            result.add(next);
        }
        return result;
    }

    private OrderResponse toResponse(Order order, Viewer viewer) {
        return toResponse(order, viewer, false);
    }

    /** {@code withTimeline}: kèm dòng thời gian (một truy vấn) — chỉ dùng cho API một đơn. */
    private OrderResponse toResponse(Order order, Viewer viewer, boolean withTimeline) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setCode(order.getCode());
        res.setOrderDate(order.getOrderDate());
        res.setCompletedAt(order.getCompletedAt());
        res.setTotalAmount(order.getTotalAmount());
        res.setDiscountAmount(order.getDiscountAmount());
        res.setShippingFee(order.getShippingFee());
        res.setFinalAmount(order.getFinalAmount());
        res.setStatus(order.getStatus());
        res.setPaymentStatus(order.getPaymentStatus());
        res.setPaymentMethod(order.getPaymentMethod());
        res.setCouponCode(order.getCouponCode());
        res.setDiscountNote(order.getDiscountNote());
        res.setCoinsUsed(order.getCoinsUsed());
        res.setCoinsEarned(order.getCoinsEarned());
        if (order.getStatus() != OrderStatus.DA_HUY && order.getStatus() != OrderStatus.HOAN_THANH) {
            res.setCoinsReward(coinWallet.expectedReward(order));
        }
        res.setReceiverName(order.getReceiverName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setShippingAddress(order.getShippingAddress());
        res.setNote(order.getNote());
        if (order.getStore() != null) {
            res.setStoreId(order.getStore().getId());
            res.setStoreName(order.getStore().getName());
        }
        Customer customer = order.getCustomer();
        if (customer != null) {
            res.setCustomerId(customer.getId());
            User account = customer.getUser();
            res.setCustomerName(customer.getFullName() != null
                    ? customer.getFullName()
                    : account != null ? (account.getFullName() != null ? account.getFullName() : account.getUsername()) : null);
            res.setCustomerPhone(customer.getPhone() != null ? customer.getPhone() : account != null ? account.getPhone() : null);
        }
        if (order.getHandledBy() != null) {
            User staff = order.getHandledBy();
            res.setHandledByName(staff.getFullName() != null ? staff.getFullName() : staff.getUsername());
        }

        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = item.getProductVariant();
                OrderResponse.OrderItemResponse ir = new OrderResponse.OrderItemResponse();
                ir.setProductVariantId(variant.getId());
                ir.setProductId(variant.getProduct().getId());
                ir.setProductName(variant.getProduct().getName());
                ir.setColor(variant.getColor());
                ir.setSize(variant.getSize());
                ir.setPathImage(variant.getImageUrl() != null ? variant.getImageUrl() : variant.getProduct().getImageUrl());
                ir.setQuantity(item.getQuantity());
                ir.setOriginalUnitPrice(item.getOriginalUnitPrice());
                ir.setUnitPrice(item.getUnitPrice());
                ir.setDiscount(item.getDiscount());
                ir.setLineTotal(item.getLineTotal());
                itemResponses.add(ir);
            }
        }
        res.setItems(itemResponses);
        res.setAllowedNextStatuses(allowedNext(order, viewer));
        res.setCancelReason(order.getCancelReason());
        res.setReturnDeadline(returnPolicy.deadline(order));
        if (withTimeline && order.getId() != null) {
            res.setTimeline(timeline.entries(order, viewer == null ? null : viewer.user()));
        }
        return res;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
