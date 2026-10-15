package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.PurchaseOrderRequest;
import com.example.elsa_store.dto.response.PurchaseOrderResponse;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.PurchaseOrder;
import com.example.elsa_store.entity.PurchaseOrderItem;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.Supplier;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.PurchaseOrderRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.SupplierRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreRepository storeRepository;
    private final StockService stockService;
    private final AccessGuard accessGuard;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    SupplierRepository supplierRepository,
                                    ProductVariantRepository variantRepository,
                                    StoreRepository storeRepository,
                                    StockService stockService,
                                    AccessGuard accessGuard) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.variantRepository = variantRepository;
        this.storeRepository = storeRepository;
        this.stockService = stockService;
        this.accessGuard = accessGuard;
    }

    // ------------------------------------------------------------------ xem

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> list(Integer status, Long storeId) {
        Long scope;
        if (accessGuard.currentUser().getRole() == Role.ADMIN) {
            if (storeId != null && !storeRepository.existsById(storeId)) {
                throw new ResourceNotFoundException("Store not found with id=" + storeId);
            }
            scope = storeId;
        } else {
            scope = accessGuard.requireStoreManager().getId();
            if (storeId != null && !storeId.equals(scope)) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
        }
        return purchaseOrderRepository.search(scope, status).stream().map(p -> toResponse(p, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        PurchaseOrder purchase = find(id);
        accessGuard.authorize(purchase.getStore(), Access.MANAGER_READ);
        return toResponse(purchase, true);
    }

    // ------------------------------------------------------------------ phiếu nháp

    @Override
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        Store store = accessGuard.requireStoreManager();
        PurchaseOrder purchase = new PurchaseOrder();
        purchase.setStore(store);
        purchase.setCreatedBy(accessGuard.currentUser());
        purchase.setStatus(PurchaseOrder.DRAFT);
        purchase.setCode(newCode());
        purchase.setItems(new ArrayList<>());
        fill(purchase, request);
        return toResponse(purchaseOrderRepository.save(purchase), true);
    }

    @Override
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder purchase = lock(id);
        accessGuard.authorize(purchase.getStore(), Access.MANAGER_WRITE);
        requireDraft(purchase);
        fill(purchase, request);
        return toResponse(purchaseOrderRepository.save(purchase), true);
    }

    @Override
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrder purchase = lock(id);
        accessGuard.authorize(purchase.getStore(), Access.MANAGER_WRITE);
        if (purchase.getStatus() != null && purchase.getStatus() == PurchaseOrder.CANCELLED) {
            return toResponse(purchase, true);
        }
        requireDraft(purchase);
        purchase.setStatus(PurchaseOrder.CANCELLED);
        return toResponse(purchaseOrderRepository.save(purchase), true);
    }

    @Override
    public void delete(Long id) {
        PurchaseOrder purchase = lock(id);
        accessGuard.authorize(purchase.getStore(), Access.MANAGER_WRITE);
        if (purchase.getStatus() != null && purchase.getStatus() == PurchaseOrder.RECEIVED) {
            throw new AppException(ErrorCode.PURCHASE_ORDER_LOCKED);
        }
        purchaseOrderRepository.delete(purchase);
    }

    // ------------------------------------------------------------------ nhập kho

    @Override
    public PurchaseOrderResponse receive(Long id) {
        PurchaseOrder purchase = lock(id);
        User actor = accessGuard.authorize(purchase.getStore(), Access.MANAGER_WRITE);
        requireDraft(purchase);
        if (purchase.getItems() == null || purchase.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Purchase order has no items");
        }
        // Cùng thứ tự khoá dòng tồn kho với đặt đơn (theo id biến thể) để không chờ chéo nhau.
        List<PurchaseOrderItem> lines = new ArrayList<>(purchase.getItems());
        lines.sort(Comparator.comparing(i -> i.getProductVariant().getId()));
        StockService.Ref ref = StockService.Ref.purchase(purchase);
        for (PurchaseOrderItem line : lines) {
            stockService.receive(line.getProductVariant(), line.getQuantity(), line.getUnitPrice(), ref, actor);
        }
        purchase.setStatus(PurchaseOrder.RECEIVED);
        purchase.setReceivedAt(LocalDateTime.now());
        return toResponse(purchaseOrderRepository.save(purchase), true);
    }

    // ------------------------------------------------------------------ nội bộ

    /** Ghi nhà cung cấp, ghi chú và thay toàn bộ dòng hàng theo yêu cầu; tính lại tổng tiền. */
    private void fill(PurchaseOrder purchase, PurchaseOrderRequest request) {
        Store store = purchase.getStore();
        purchase.setSupplier(supplierOf(store, request.getSupplierId()));
        String note = request.getNote() == null ? null : request.getNote().trim();
        purchase.setNote(note == null || note.isEmpty() ? null : note);

        Set<Long> seen = new HashSet<>();
        List<PurchaseOrderItem> lines = new ArrayList<>();
        double total = 0;
        for (PurchaseOrderRequest.Line wanted : request.getItems()) {
            if (!seen.add(wanted.getProductVariantId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Each product variant can appear only once");
            }
            ProductVariant variant = variantRepository.findById(wanted.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
            Store owner = variant.getProduct() == null ? null : variant.getProduct().getStore();
            if (owner == null || !owner.getId().equals(store.getId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Product belongs to another store");
            }
            double unitPrice = Math.round(wanted.getUnitPrice());
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(purchase);
            item.setProductVariant(variant);
            item.setQuantity(wanted.getQuantity());
            item.setUnitPrice(unitPrice);
            lines.add(item);
            total += unitPrice * wanted.getQuantity();
        }
        // Thay trong chính danh sách đang quản lý để orphanRemoval xoá các dòng cũ.
        purchase.getItems().clear();
        purchase.getItems().addAll(lines);
        purchase.setTotalAmount(total);
    }

    private Supplier supplierOf(Store store, Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        if (supplier.getStore() == null || !supplier.getStore().getId().equals(store.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Supplier belongs to another store");
        }
        return supplier;
    }

    private static void requireDraft(PurchaseOrder purchase) {
        Integer status = purchase.getStatus();
        if (status != null && status != PurchaseOrder.DRAFT) {
            throw new AppException(ErrorCode.PURCHASE_ORDER_LOCKED);
        }
    }

    private PurchaseOrder find(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder not found"));
    }

    private PurchaseOrder lock(Long id) {
        PurchaseOrder purchase = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder not found"));
        if (purchase.getItems() == null) {
            purchase.setItems(new ArrayList<>());
        }
        return purchase;
    }

    private String newCode() {
        String prefix = "PN-" + LocalDateTime.now().format(CODE_DATE) + "-";
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(prefix);
            for (int i = 0; i < 4; i++) {
                code.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            }
            if (!purchaseOrderRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_UPDATE);
    }

    static String statusName(Integer status) {
        if (status == null || status == PurchaseOrder.DRAFT) return "DRAFT";
        if (status == PurchaseOrder.RECEIVED) return "RECEIVED";
        return "CANCELLED";
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder p, boolean withItems) {
        List<PurchaseOrderItem> items = p.getItems() == null ? List.of() : p.getItems();
        int totalQuantity = items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        User creator = p.getCreatedBy();
        PurchaseOrderResponse res = PurchaseOrderResponse.builder()
                .id(p.getId())
                .code(p.getCode() != null ? p.getCode() : "PN-" + p.getId())
                .status(p.getStatus() == null ? PurchaseOrder.DRAFT : p.getStatus())
                .statusName(statusName(p.getStatus()))
                .supplierId(p.getSupplier() == null ? null : p.getSupplier().getId())
                .supplierName(p.getSupplier() == null ? null : p.getSupplier().getName())
                .note(p.getNote())
                .totalAmount(p.getTotalAmount() == null ? 0.0 : p.getTotalAmount())
                .totalQuantity(totalQuantity)
                .itemCount(items.size())
                .createdByName(creator == null ? null
                        : creator.getFullName() != null ? creator.getFullName() : creator.getUsername())
                .createdAt(p.getCreatedAt())
                .receivedAt(p.getReceivedAt())
                .build();
        if (withItems) {
            Map<Long, Integer> stock = stockService.levels(
                    items.stream().map(i -> i.getProductVariant().getId()).toList());
            res.setItems(items.stream().map(i -> line(i, stock)).toList());
        }
        return res;
    }

    private static PurchaseOrderResponse.Line line(PurchaseOrderItem item, Map<Long, Integer> stock) {
        ProductVariant v = item.getProductVariant();
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        double unitPrice = item.getUnitPrice() == null ? 0 : item.getUnitPrice();
        return PurchaseOrderResponse.Line.builder()
                .id(item.getId())
                .productVariantId(v.getId())
                .productId(v.getProduct().getId())
                .productName(v.getProduct().getName())
                .color(v.getColor())
                .size(v.getSize())
                .sku(v.getSku())
                .imageUrl(v.getImageUrl() != null ? v.getImageUrl() : v.getProduct().getImageUrl())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice * quantity)
                .currentStock(stock.get(v.getId()))
                .costPrice(v.getCostPrice())
                .build();
    }
}
