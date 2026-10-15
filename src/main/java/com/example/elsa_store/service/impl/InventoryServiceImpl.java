package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.InventoryRequest;
import com.example.elsa_store.dto.request.StockAdjustRequest;
import com.example.elsa_store.dto.response.InventoryResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StockItemResponse;
import com.example.elsa_store.dto.response.StockMovementResponse;
import com.example.elsa_store.dto.response.StockOverviewResponse;
import com.example.elsa_store.entity.Inventory;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.StockMovement;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.InventoryMapper;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.StockMovementRepository;
import com.example.elsa_store.service.InventoryService;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tồn kho. Mọi thay đổi số lượng đi qua {@link StockService} để thẻ kho luôn khớp với con số tồn hiện tại (kể cả các API
 * cũ {@code /inventories} theo id dòng tồn kho).
 */
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final Set<String> FILTERS = Set.of("ALL", "TRACKED", "UNTRACKED", "LOW", "OUT");

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;
    private final AccessGuard accessGuard;

    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                ProductVariantRepository productVariantRepository,
                                StockMovementRepository movementRepository,
                                StockService stockService,
                                AccessGuard accessGuard) {
        this.inventoryRepository = inventoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.movementRepository = movementRepository;
        this.stockService = stockService;
        this.accessGuard = accessGuard;
    }

    // ------------------------------------------------------------------ API cũ theo dòng tồn kho

    @Override
    public InventoryResponse create(InventoryRequest req) {
        if (inventoryRepository.findByProductVariant_Id(req.getProductVariantId()).isPresent()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Inventory already exists for this variant");
        }
        ProductVariant variant = findVariant(req.getProductVariantId());
        User actor = accessGuard.authorize(variant.getProduct().getStore(), Access.STAFF_WRITE);
        int quantity = req.getQuantity() == null ? 0 : Math.max(0, req.getQuantity());
        Inventory i = stockService.adjust(variant, quantity, minOrZero(req.getMinQuantity()), null, actor);
        return InventoryMapper.toResponse(i);
    }

    @Override
    public InventoryResponse update(Long id, InventoryRequest req) {
        Inventory i = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        ProductVariant variant = i.getProductVariant();
        User actor = accessGuard.authorize(variant.getProduct().getStore(), Access.STAFF_WRITE);
        if (req.getProductVariantId() != null && !req.getProductVariantId().equals(variant.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Inventory cannot be moved to another variant");
        }
        int quantity = req.getQuantity() == null
                ? (i.getQuantity() == null ? 0 : i.getQuantity())
                : Math.max(0, req.getQuantity());
        i = stockService.adjust(variant, quantity, minOrNull(req.getMinQuantity()), null, actor);
        return InventoryMapper.toResponse(i);
    }

    @Override
    public void delete(Long id) {
        Inventory i = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        ProductVariant variant = i.getProductVariant();
        User actor = accessGuard.authorize(variant.getProduct().getStore(), Access.MANAGER_WRITE);
        stockService.stopTracking(variant, null, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getById(Long id) {
        Inventory i = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        accessGuard.authorize(i.getProductVariant().getProduct().getStore(), Access.READ);
        return InventoryMapper.toResponse(i);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAll() {
        var current = accessGuard.currentUser();
        List<Inventory> data = current.getRole() == com.example.elsa_store.constant.Role.ADMIN
                ? inventoryRepository.findAll()
                : inventoryRepository.findAllByProductVariant_Product_Store_Id(accessGuard.requireStoreMember().getId());
        return data.stream()
                .map(InventoryMapper::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------ màn kho hàng

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockItemResponse> stock(String keyword, String filter, int page, int size) {
        Store store = accessGuard.requireStoreManager();
        String mode = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
        if (!FILTERS.contains(mode)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "filter: invalid value " + filter);
        }
        String term = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        var result = productVariantRepository.searchStock(
                store.getId(), term, mode, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PageResponse.from(result.map(InventoryServiceImpl::toItem));
    }

    @Override
    @Transactional(readOnly = true)
    public StockOverviewResponse overview() {
        Store store = accessGuard.requireStoreManager();
        List<Object[]> rows = productVariantRepository.stockOverview(store.getId());
        return rows.isEmpty() ? StockOverviewResponse.empty() : StockOverviewResponse.fromRow(rows.get(0));
    }

    @Override
    public StockItemResponse adjust(Long variantId, StockAdjustRequest req) {
        ProductVariant variant = findVariant(variantId);
        User actor = accessGuard.authorize(variant.getProduct().getStore(), Access.MANAGER_WRITE);
        if (req.getQuantity() == null && req.getMinQuantity() == null && req.getCostPrice() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Nothing to update");
        }
        if (req.getCostPrice() != null) {
            variant.setCostPrice((double) Math.round(req.getCostPrice()));
            productVariantRepository.save(variant);
        }
        String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();
        if (req.getQuantity() != null) {
            stockService.adjust(variant, req.getQuantity(), req.getMinQuantity(), note, actor);
        } else if (req.getMinQuantity() != null) {
            Inventory inventory = inventoryRepository.findByProductVariant_Id(variantId)
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                            "Enter the stock quantity to start tracking"));
            inventory.setMinQuantity(req.getMinQuantity());
            inventoryRepository.save(inventory);
        }
        return toItem(variant);
    }

    @Override
    public StockItemResponse stopTracking(Long variantId) {
        ProductVariant variant = findVariant(variantId);
        User actor = accessGuard.authorize(variant.getProduct().getStore(), Access.MANAGER_WRITE);
        stockService.stopTracking(variant, null, actor);
        return toItem(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(Long variantId, int page, int size) {
        ProductVariant variant = findVariant(variantId);
        accessGuard.authorize(variant.getProduct().getStore(), Access.MANAGER_READ);
        var result = movementRepository.findByProductVariant_IdOrderByIdDesc(
                variantId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PageResponse.from(result.map(InventoryServiceImpl::toMovement));
    }

    // ------------------------------------------------------------------ nội bộ

    private ProductVariant findVariant(Long id) {
        return productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
    }

    private static Integer minOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static Integer minOrNull(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    static String status(Inventory inventory) {
        if (inventory == null) return "UNTRACKED";
        int quantity = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int min = inventory.getMinQuantity() == null ? 0 : inventory.getMinQuantity();
        if (quantity <= 0) return "OUT";
        if (quantity <= min) return "LOW";
        return "OK";
    }

    static StockItemResponse toItem(ProductVariant v) {
        Inventory inventory = v.getInventory();
        Integer quantity = inventory == null ? null : (inventory.getQuantity() == null ? 0 : inventory.getQuantity());
        Double value = quantity == null || v.getCostPrice() == null ? null : Math.max(0, quantity) * v.getCostPrice();
        return StockItemResponse.builder()
                .productVariantId(v.getId())
                .productId(v.getProduct().getId())
                .productName(v.getProduct().getName())
                .color(v.getColor())
                .size(v.getSize())
                .sku(v.getSku())
                .imageUrl(v.getImageUrl() != null ? v.getImageUrl() : v.getProduct().getImageUrl())
                .price(v.getPrice() != null ? v.getPrice() : v.getProduct().getBasePrice())
                .costPrice(v.getCostPrice())
                .tracked(inventory != null)
                .quantity(quantity)
                .minQuantity(inventory == null ? null : inventory.getMinQuantity())
                .status(status(inventory))
                .stockValue(value)
                .build();
    }

    private static StockMovementResponse toMovement(StockMovement m) {
        User by = m.getCreatedBy();
        return StockMovementResponse.builder()
                .id(m.getId())
                .type(m.getType())
                .quantityChange(m.getQuantityChange())
                .balanceAfter(m.getBalanceAfter())
                .unitCost(m.getUnitCost())
                .refType(m.getRefType())
                .refId(m.getRefId())
                .refCode(m.getRefCode())
                .note(m.getNote())
                .createdByName(by == null ? null : by.getFullName() != null ? by.getFullName() : by.getUsername())
                .createdAt(m.getCreatedAt())
                .build();
    }

}
