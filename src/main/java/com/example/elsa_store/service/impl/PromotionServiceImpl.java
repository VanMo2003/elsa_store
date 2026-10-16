package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.PromotionRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionResponse;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.PromotionMapper;
import com.example.elsa_store.repository.PromotionRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.PromotionProductService;
import com.example.elsa_store.service.PromotionService;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Khuyến mãi của từng cửa hàng: chỉ chủ và quản lý thao tác; ADMIN chỉ xem. */
@Service
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final StoreRepository storeRepository;
    private final AccessGuard accessGuard;
    private final PricingService pricingService;
    private final PromotionProductService promotionProductService;
    private PriceDropAlerts priceDropAlerts;

    public PromotionServiceImpl(
            PromotionRepository promotionRepository,
            StoreRepository storeRepository,
            AccessGuard accessGuard,
            PricingService pricingService,
            PromotionProductService promotionProductService) {
        this.promotionRepository = promotionRepository;
        this.storeRepository = storeRepository;
        this.accessGuard = accessGuard;
        this.promotionProductService = promotionProductService;
        this.pricingService = pricingService;
    }

    @Override
    public PromotionResponse create(PromotionRequest req) {
        Store store = accessGuard.requireStoreManager();
        validate(req, store, null);
        Promotion p = PromotionMapper.toEntity(req);
        p.setStore(store);
        p = promotionRepository.save(p);
        PromotionResponse res = withProducts(p, req.getProductIds());
        alertWishlists(p, Set.of(), res);
        return res;
    }

    @Override
    public PromotionResponse update(Long id, PromotionRequest req) {
        Promotion p = find(id);
        accessGuard.authorize(p.getStore(), Access.MANAGER_WRITE);
        validate(req, p.getStore(), p.getId());
        Set<Long> before = PriceDropAlerts.discountedBy(p, pricingService.today());
        PromotionMapper.update(p, req);
        PromotionResponse res = withProducts(p, req.getProductIds());
        alertWishlists(p, before, res);
        return res;
    }

    /** Báo khách thả tim các sản phẩm vừa được giảm giá (tiêm qua setter để giữ nguyên hàm dựng dùng trong test). */
    @Autowired
    void setPriceDropAlerts(PriceDropAlerts priceDropAlerts) {
        this.priceDropAlerts = priceDropAlerts;
    }

    private void alertWishlists(Promotion p, Set<Long> before, PromotionResponse res) {
        if (priceDropAlerts != null) {
            priceDropAlerts.afterChange(p, before, res.getProductIds(), pricingService.today());
        }
    }

    /**
     * Gắn lại sản phẩm (nếu yêu cầu có gửi) trong cùng giao dịch rồi trả khuyến mãi với đúng danh sách vừa lưu.
     *
     * <p>Phải gắn <b>trước</b> khi dựng response: response đọc {@code promotionProducts} (cascade ALL), nếu danh sách cũ
     * đã nạp vào bộ nhớ thì lúc flush Hibernate lưu lại chính các liên kết vừa xoá — khuyến mãi chuyển sang "giảm cả đơn"
     * mà sản phẩm vẫn bị giảm giá.
     */
    private PromotionResponse withProducts(Promotion p, List<Long> productIds) {
        List<Long> linked = null;
        if (productIds != null) {
            if (p.getPromotionProducts() != null && Hibernate.isInitialized(p.getPromotionProducts())) {
                p.getPromotionProducts().clear();
            }
            linked = promotionProductService.setProducts(p.getId(), productIds).stream()
                    .map(ProductResponse::getId)
                    .toList();
        }
        PromotionResponse res = PromotionMapper.toResponse(p, pricingService.today());
        if (linked != null) {
            res.setProductIds(linked);
        }
        return res;
    }

    @Override
    public void delete(Long id) {
        Promotion p = find(id);
        accessGuard.authorize(p.getStore(), Access.MANAGER_WRITE);
        promotionRepository.delete(p);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getById(Long id) {
        Promotion p = find(id);
        accessGuard.authorize(p.getStore(), Access.MANAGER_READ);
        return PromotionMapper.toResponse(p, pricingService.today());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getAll() {
        var current = accessGuard.currentUser();
        List<Promotion> data = current.getRole() == Role.ADMIN
                ? promotionRepository.findAll()
                : promotionRepository.findAllByStore_Id(accessGuard.requireStoreManager().getId());
        var today = pricingService.today();
        return data.stream().map(p -> PromotionMapper.toResponse(p, today)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> activeCoupons(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .filter(Store::isApproved)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        var today = pricingService.today();
        return promotionRepository.findActiveCoupons(store.getId(), today).stream()
                .map(p -> PromotionMapper.toResponse(p, today))
                .toList();
    }

    /** Dữ liệu sai làm tính giá sai (giảm 500%, ngày kết thúc trước ngày bắt đầu…) nên chặn ngay khi lưu. */
    private void validate(PromotionRequest req, Store store, Long selfId) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "name: must not be blank");
        }
        Integer type = req.getType();
        if (type == null || (type != PricingService.TYPE_FIXED_AMOUNT && type != PricingService.TYPE_PERCENTAGE)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "type: must be 1 (amount) or 2 (percent)");
        }
        if (req.getValue() == null || req.getValue() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "value: must be greater than 0");
        }
        if (type == PricingService.TYPE_PERCENTAGE && req.getValue() > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "value: must be at most 100 percent");
        }
        if (req.getMinOrderValue() != null && req.getMinOrderValue() < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "minOrderValue: must be at least 0");
        }
        if (req.getStartDate() != null && req.getEndDate() != null && req.getEndDate().isBefore(req.getStartDate())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "from must not be after to");
        }
        if (req.getStatus() != null && req.getStatus() != 0 && req.getStatus() != 1) {
            throw new AppException(ErrorCode.BAD_REQUEST, "status: must be 0 or 1");
        }
        String code = PromotionMapper.normalizeCoupon(req.getCouponCode());
        if (code != null) {
            if (!code.matches("[A-Z0-9_-]{3,30}")) {
                throw new AppException(ErrorCode.BAD_REQUEST, "couponCode: invalid value");
            }
            boolean taken = promotionRepository.findByStoreAndCoupon(store.getId(), code).stream()
                    .anyMatch(other -> !Objects.equals(other.getId(), selfId));
            if (taken) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Coupon code already exists in this store");
            }
        }
    }

    private Promotion find(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
    }
}
