package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.HomeHighlightsResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.PromotionProductRepository;
import com.example.elsa_store.service.HomeHighlightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trang chủ công khai: chỉ sản phẩm đang bán của cửa hàng đã duyệt. "Bán chạy" đếm số món của đơn đã hoàn thành trong
 * {@link #BEST_SELLER_DAYS} ngày; chưa có đơn nào trong khoảng đó thì tính trên toàn bộ lịch sử.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeHighlightsServiceImpl implements HomeHighlightsService {

    static final int BEST_SELLER_DAYS = 30;

    private final ProductRepository productRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final OrderItemRepository orderItemRepository;
    private final PricingService pricingService;
    private final CatalogPresenter catalog;

    @Override
    public HomeHighlightsResponse highlights(int size) {
        int limit = Math.min(Math.max(size, 1), 20);
        return new HomeHighlightsResponse(onSale(limit), bestSellers(limit));
    }

    private List<ProductResponse> onSale(int limit) {
        List<Product> products = visible(promotionProductRepository.productIdsOnSale(pricingService.today()));
        return catalog.products(products).stream()
                .filter(p -> p.getSalePrice() != null)
                .sorted(Comparator.comparing((ProductResponse p) -> p.getDiscountPercent() == null ? 0 : p.getDiscountPercent())
                        .reversed()
                        .thenComparing(ProductResponse::getId, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private List<ProductResponse> bestSellers(int limit) {
        // Lấy dư vì vài sản phẩm bán chạy có thể đã bị ẩn.
        PageRequest page = PageRequest.of(0, limit * 2);
        List<Long> ids = orderItemRepository.bestSellingProductIds(
                LocalDateTime.now().minusDays(BEST_SELLER_DAYS), page);
        if (ids.isEmpty()) {
            ids = orderItemRepository.bestSellingProductIds(LocalDateTime.of(2000, 1, 1, 0, 0), page);
        }
        List<Product> products = visible(ids);
        return catalog.products(products.stream().limit(limit).toList());
    }

    /** Sản phẩm đang bán theo đúng thứ tự {@code ids}. */
    private List<Product> visible(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findAllById(ids).stream()
                .filter(ProductServiceImpl::isPublic)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
