package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.ReturnStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ReturnCreateRequest;
import com.example.elsa_store.dto.request.ReturnDecisionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReturnResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ReturnRequestRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.ReturnService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Đổi / trả hàng. Khách gửi yêu cầu cho đơn đã hoàn thành trong thời hạn ({@link ReturnPolicy}), mỗi đơn một yêu cầu đang
 * mở và không trả quá số đã mua. Cửa hàng (chủ, quản lý, nhân viên) duyệt hoặc từ chối; chủ / quản lý xác nhận đã nhận
 * hàng và hoàn tiền — lúc đó hàng về kho (biến thể có theo dõi kho) và tiền hoàn được báo cáo trừ vào doanh thu.
 */
@Service
@Transactional
public class ReturnServiceImpl implements ReturnService {

    static final Set<ReturnStatus> OPEN = EnumSet.of(ReturnStatus.CHO_DUYET, ReturnStatus.DA_DUYET);
    /** Các yêu cầu đã / đang chiếm số lượng có thể trả của đơn. */
    static final Set<ReturnStatus> COUNTED =
            EnumSet.of(ReturnStatus.CHO_DUYET, ReturnStatus.DA_DUYET, ReturnStatus.DA_HOAN_TIEN);
    static final String CANCEL = "CANCEL";
    static final String APPROVE = "APPROVE";
    static final String REJECT = "REJECT";
    static final String REFUND = "REFUND";
    static final int MAX_PAGE_SIZE = 50;

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final AccessGuard accessGuard;
    private final StockService stockService;
    private final NotificationService notificationService;
    private final ReturnPolicy policy;
    private final Clock clock;

    @Autowired
    public ReturnServiceImpl(
            ReturnRequestRepository returnRepository,
            OrderRepository orderRepository,
            AccessGuard accessGuard,
            StockService stockService,
            NotificationService notificationService,
            ReturnPolicy policy) {
        this(returnRepository, orderRepository, accessGuard, stockService, notificationService, policy,
                Clock.systemDefaultZone());
    }

    ReturnServiceImpl(
            ReturnRequestRepository returnRepository,
            OrderRepository orderRepository,
            AccessGuard accessGuard,
            StockService stockService,
            NotificationService notificationService,
            ReturnPolicy policy,
            Clock clock) {
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.accessGuard = accessGuard;
        this.stockService = stockService;
        this.notificationService = notificationService;
        this.policy = policy;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ khách

    @Override
    public ReturnResponse create(ReturnCreateRequest request) {
        User current = accessGuard.currentUser();
        if (current.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Chỉ tài khoản khách hàng gửi được yêu cầu trả hàng");
        }
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getUser() == null || !order.getUser().getId().equals(current.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        if (order.getStatus() != OrderStatus.HOAN_THANH) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ trả hàng được với đơn đã hoàn thành");
        }
        LocalDateTime deadline = policy.deadline(order);
        if (deadline == null || LocalDateTime.now(clock).isAfter(deadline)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Đã quá hạn trả hàng (" + policy.windowDays() + " ngày sau khi nhận hàng)");
        }
        if (returnRepository.existsByOrder_IdAndStatusIn(order.getId(), OPEN)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn này đang có yêu cầu trả hàng chờ xử lý");
        }
        String reason = trimToNull(request.getReason());
        if (reason == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "reason: must not be blank");
        }

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (ReturnCreateRequest.Line line : request.getItems()) {
            if (line.getProductVariantId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "items: invalid line");
            }
            wanted.merge(line.getProductVariantId(), line.getQuantity(), Integer::sum);
        }
        Map<Long, OrderItem> byVariant = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            byVariant.put(item.getProductVariant().getId(), item);
        }
        Map<Long, Long> already = new HashMap<>();
        for (Object[] row : returnRepository.returnedQuantities(order.getId(), COUNTED)) {
            already.put((Long) row[0], ((Number) row[1]).longValue());
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .store(order.getStore())
                .user(current)
                .status(ReturnStatus.CHO_DUYET)
                .reason(reason)
                .note(trimToNull(request.getNote()))
                .build();
        double ratio = paidRatio(order);
        double suggested = 0;
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            OrderItem item = byVariant.get(entry.getKey());
            if (item == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Sản phẩm không có trong đơn hàng");
            }
            long left = nz(item.getQuantity()) - already.getOrDefault(item.getId(), 0L);
            if (entry.getValue() > left) {
                throw new AppException(ErrorCode.BAD_REQUEST, left <= 0
                        ? "Sản phẩm \"" + StockService.label(item.getProductVariant()) + "\" đã được trả hết"
                        : "Chỉ còn trả được " + left + " sản phẩm \"" + StockService.label(item.getProductVariant()) + "\"");
            }
            double unit = PricingService.money(unitPaid(item) * ratio);
            returnRequest.getItems().add(ReturnItem.builder()
                    .request(returnRequest)
                    .orderItem(item)
                    .quantity(entry.getValue())
                    .unitRefund(unit)
                    .build());
            suggested += unit * entry.getValue();
        }
        returnRequest.setSuggestedRefund(PricingService.money(suggested));
        returnRequest.setCode(newCode());
        returnRequest = returnRepository.save(returnRequest);

        if (order.getStore() != null) {
            notificationService.notifyStore(order.getStore(), NotificationType.RETURN_REQUESTED,
                    NotificationMessages.returnRequested(returnRequest, displayName(current)), AppLinks.returns(),
                    returnRequest.getId());
        }
        return toResponse(returnRequest, current);
    }

    @Override
    public ReturnResponse cancel(Long id) {
        ReturnRequest r = find(id);
        User current = accessGuard.currentUser();
        if (!r.getUser().getId().equals(current.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        if (r.getStatus() == ReturnStatus.DA_HUY) {
            return toResponse(r, current);
        }
        if (r.getStatus() != ReturnStatus.CHO_DUYET) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cửa hàng đã xử lý yêu cầu này, không huỷ được nữa");
        }
        r.setStatus(ReturnStatus.DA_HUY);
        return toResponse(returnRepository.save(r), current);
    }

    // ------------------------------------------------------------------ cửa hàng

    @Override
    public ReturnResponse approve(Long id, ReturnDecisionRequest request) {
        ReturnRequest r = find(id);
        User staff = accessGuard.authorize(r.getStore(), Access.STAFF_WRITE);
        if (r.getStatus() != ReturnStatus.CHO_DUYET) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu không còn chờ duyệt");
        }
        r.setStatus(ReturnStatus.DA_DUYET);
        decide(r, staff, request == null ? null : request.getNote());
        r = returnRepository.save(r);
        notifyCustomer(r, NotificationMessages.returnApproved(r));
        return toResponse(r, staff);
    }

    @Override
    public ReturnResponse reject(Long id, ReturnDecisionRequest request) {
        ReturnRequest r = find(id);
        User staff = accessGuard.authorize(r.getStore(), Access.STAFF_WRITE);
        if (!OPEN.contains(r.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu đã được xử lý");
        }
        String note = request == null ? null : trimToNull(request.getNote());
        if (note == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Hãy cho khách biết lý do từ chối");
        }
        r.setStatus(ReturnStatus.TU_CHOI);
        decide(r, staff, note);
        r = returnRepository.save(r);
        notifyCustomer(r, NotificationMessages.returnRejected(r));
        return toResponse(r, staff);
    }

    @Override
    public ReturnResponse refund(Long id, ReturnDecisionRequest request) {
        ReturnRequest r = find(id);
        User manager = accessGuard.authorize(r.getStore(), Access.MANAGER_WRITE);
        if (!OPEN.contains(r.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu đã được xử lý");
        }
        Double requested = request == null ? null : request.getRefundAmount();
        double amount = PricingService.money(requested != null ? requested : nzd(r.getSuggestedRefund()));
        double paid = nzd(r.getOrder().getFinalAmount());
        if (amount < 0 || amount > paid) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền hoàn phải từ 0 đến " + NotificationMessages.money(paid));
        }
        r.setStatus(ReturnStatus.DA_HOAN_TIEN);
        r.setRefundAmount(amount);
        r.setRefundedAt(LocalDateTime.now(clock));
        String note = request == null ? null : trimToNull(request.getNote());
        if (r.getDecidedAt() == null) {
            decide(r, manager, note);
        } else if (note != null) {
            r.setStoreNote(note);
        }
        // Hàng về kho theo thứ tự id biến thể (như lúc bán) để không khoá chéo với đơn khác.
        List<ReturnItem> items = new ArrayList<>(r.getItems());
        items.sort(Comparator.comparing(i -> i.getOrderItem().getProductVariant().getId()));
        for (ReturnItem item : items) {
            stockService.restock(item.getOrderItem().getProductVariant(), nz(item.getQuantity()),
                    item.getOrderItem().getCostPrice(), StockService.Ref.order(r.getOrder()),
                    "Khách trả hàng " + r.getCode(), manager);
        }
        r = returnRepository.save(r);
        notifyCustomer(r, NotificationMessages.returnRefunded(r));
        return toResponse(r, manager);
    }

    // ------------------------------------------------------------------ đọc

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        ReturnRequest r = find(id);
        User current = accessGuard.currentUser();
        if (current.getRole() == Role.USER) {
            if (!r.getUser().getId().equals(current.getId())) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
        } else {
            accessGuard.authorize(r.getStore(), Access.READ);
        }
        return toResponse(r, current);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> mine(Long orderId, int page, int size) {
        User current = accessGuard.currentUser();
        return PageResponse.from(returnRepository.findMine(current.getId(), orderId, pageable(page, size))
                .map(r -> toResponse(r, current)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> forStore(ReturnStatus status, Long storeId, int page, int size) {
        User current = accessGuard.currentUser();
        Long scope;
        if (current.getRole() == Role.ADMIN) {
            scope = storeId;
        } else if (current.getRole().isStoreRole()) {
            scope = accessGuard.requireStoreMember().getId();
        } else {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return PageResponse.from(returnRepository.findForStore(scope, status, pageable(page, size))
                .map(r -> toResponse(r, current)));
    }

    // ------------------------------------------------------------------ nội bộ

    private ReturnRequest find(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
    }

    private void decide(ReturnRequest r, User by, String note) {
        r.setDecidedAt(LocalDateTime.now(clock));
        r.setDecidedBy(by);
        String text = trimToNull(note);
        if (text != null) {
            r.setStoreNote(text);
        }
    }

    private void notifyCustomer(ReturnRequest r, NotificationMessages.Message message) {
        notificationService.notify(r.getUser(), NotificationType.RETURN_UPDATED, message, AppLinks.returns(), r.getId());
    }

    /**
     * Tỷ lệ khách thực trả trên giá trị các dòng hàng: phần giảm giá cả đơn (mã giảm giá, khuyến mãi theo đơn) chia đều
     * theo giá trị từng món; phí giao không hoàn.
     */
    static double paidRatio(Order order) {
        double lines = 0;
        for (OrderItem item : order.getItems()) {
            lines += unitPaid(item) * nz(item.getQuantity());
        }
        double paidGoods = nzd(order.getFinalAmount()) - nzd(order.getShippingFee());
        if (lines <= 0 || paidGoods <= 0) {
            return lines <= 0 ? 1 : 0;
        }
        return Math.min(1, paidGoods / lines);
    }

    /** Giá một món sau khuyến mãi sản phẩm (từ thành tiền của dòng; đơn cũ thiếu thì lấy đơn giá). */
    static double unitPaid(OrderItem item) {
        int quantity = nz(item.getQuantity());
        if (item.getLineTotal() != null && quantity > 0) {
            return item.getLineTotal() / quantity;
        }
        return nzd(item.getUnitPrice());
    }

    private ReturnResponse toResponse(ReturnRequest r, User viewer) {
        ReturnResponse res = new ReturnResponse();
        res.setId(r.getId());
        res.setCode(r.getCode());
        Order order = r.getOrder();
        res.setOrderId(order.getId());
        res.setOrderCode(order.getCode());
        if (r.getStore() != null) {
            res.setStoreId(r.getStore().getId());
            res.setStoreName(r.getStore().getName());
        }
        res.setCustomerName(displayName(r.getUser()));
        res.setCustomerPhone(order.getReceiverPhone() != null ? order.getReceiverPhone() : r.getUser().getPhone());
        res.setStatus(r.getStatus());
        res.setReason(r.getReason());
        res.setNote(r.getNote());
        res.setStoreNote(r.getStoreNote());
        res.setSuggestedRefund(r.getSuggestedRefund());
        res.setRefundAmount(r.getRefundAmount());
        res.setCreatedAt(r.getCreatedAt());
        res.setDecidedAt(r.getDecidedAt());
        boolean customerView = viewer != null && viewer.getRole() == Role.USER;
        res.setDecidedByName(customerView || r.getDecidedBy() == null ? null : displayName(r.getDecidedBy()));
        res.setRefundedAt(r.getRefundedAt());
        List<ReturnResponse.Item> items = new ArrayList<>();
        for (ReturnItem item : r.getItems()) {
            ProductVariant v = item.getOrderItem().getProductVariant();
            ReturnResponse.Item line = new ReturnResponse.Item();
            line.setProductVariantId(v.getId());
            line.setProductId(v.getProduct() == null ? null : v.getProduct().getId());
            line.setProductName(v.getProduct() == null ? null : v.getProduct().getName());
            line.setColor(v.getColor());
            line.setSize(v.getSize());
            line.setImageUrl(v.getImageUrl() != null ? v.getImageUrl() : v.getProduct() == null ? null : v.getProduct().getImageUrl());
            line.setQuantity(item.getQuantity());
            line.setUnitRefund(item.getUnitRefund());
            line.setLineRefund(PricingService.money(nzd(item.getUnitRefund()) * nz(item.getQuantity())));
            items.add(line);
        }
        res.setItems(items);
        res.setAllowedActions(allowedActions(r, viewer));
        return res;
    }

    private List<String> allowedActions(ReturnRequest r, User viewer) {
        if (viewer == null) {
            return List.of();
        }
        if (viewer.getRole() == Role.USER) {
            return r.getUser().getId().equals(viewer.getId()) && r.getStatus() == ReturnStatus.CHO_DUYET
                    ? List.of(CANCEL)
                    : List.of();
        }
        if (!viewer.getRole().isStoreRole() || !OPEN.contains(r.getStatus())) {
            return List.of();
        }
        Store mine = accessGuard.storeOf(viewer);
        if (mine == null || !mine.isApproved() || r.getStore() == null || !mine.getId().equals(r.getStore().getId())) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (r.getStatus() == ReturnStatus.CHO_DUYET) {
            actions.add(APPROVE);
        }
        actions.add(REJECT);
        if (viewer.getRole() == Role.STORE_OWNER || viewer.getRole() == Role.STORE_MANAGER) {
            actions.add(REFUND);
        }
        return actions;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder("TH-").append(CODE_DATE.format(LocalDateTime.now(clock))).append('-');
            for (int i = 0; i < 4; i++) {
                code.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            }
            if (!returnRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_UPDATE);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
    }

    private static String displayName(User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static double nzd(Double value) {
        return value == null ? 0 : value;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
