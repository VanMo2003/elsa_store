package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.ProductVariantDetailResponse;
import com.example.elsa_store.dto.response.ProductVariantResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.mapper.ProductMapper;
import com.example.elsa_store.mapper.ProductVariantMapper;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển sản phẩm / biến thể sang DTO kèm giá sau khuyến mãi và tình trạng còn hàng — vài truy vấn gộp cho cả trang,
 * không truy vấn lại theo từng sản phẩm.
 */
@Component
public class CatalogPresenter {

    private final PricingService pricingService;
    private final StockService stockService;
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductReviewRepository reviewRepository;

    public CatalogPresenter(
            PricingService pricingService,
            StockService stockService,
            InventoryRepository inventoryRepository,
            ProductVariantRepository productVariantRepository,
            ProductReviewRepository reviewRepository) {
        this.pricingService = pricingService;
        this.stockService = stockService;
        this.inventoryRepository = inventoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<ProductResponse> products(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, List<Promotion>> promos = pricingService.productPromotions(ids);
        Map<Long, Boolean> stock = inStock(ids);
        Map<Long, double[]> ratings = ProductReviewServiceImpl.stats(reviewRepository, ids);
        return products.stream()
                .map(product -> {
                    ProductResponse res = ProductMapper.toResponse(product);
                    PricingService.Discounted price = PricingService.best(product.getBasePrice(), promos.get(product.getId()));
                    if (price.discounted()) {
                        res.setSalePrice(price.salePrice());
                        res.setDiscountPercent(price.percentOff());
                        res.setPromotionName(price.promotionName());
                        res.setPromotionEndsAt(price.promotionEndsAt());
                    }
                    res.setInStock(stock.get(product.getId()));
                    double[] rating = ratings.get(product.getId());
                    if (rating != null) {
                        res.setRatingAverage(rating[0]);
                        res.setRatingCount((long) rating[1]);
                    }
                    return res;
                })
                .toList();
    }

    public ProductResponse product(Product product) {
        return products(List.of(product)).getFirst();
    }

    public ProductDetailResponse detail(Product product, List<ProductVariant> variants, List<String> images) {
        ProductDetailResponse res = ProductMapper.toDetailResponse(product);
        List<Promotion> promos = pricingService.productPromotions(List.of(product.getId())).get(product.getId());
        PricingService.Discounted price = PricingService.best(product.getBasePrice(), promos);
        if (price.discounted()) {
            res.setSalePrice(price.salePrice());
            res.setDiscountPercent(price.percentOff());
            res.setPromotionName(price.promotionName());
            res.setPromotionEndsAt(price.promotionEndsAt());
        }
        res.setProductVariants(variants(variants, promos));
        res.setImages(images);
        res.setInStock(inStock(List.of(product.getId())).get(product.getId()));
        double[] rating = ProductReviewServiceImpl.stats(reviewRepository, List.of(product.getId())).get(product.getId());
        if (rating != null) {
            res.setRatingAverage(rating[0]);
            res.setRatingCount((long) rating[1]);
        }
        return res;
    }

    public List<ProductVariantResponse> variants(List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = variants.stream().map(v -> v.getProduct().getId()).distinct().toList();
        Map<Long, List<Promotion>> promos = pricingService.productPromotions(productIds);
        Map<Long, Integer> stock = stockService.levels(variants.stream().map(ProductVariant::getId).toList());
        return variants.stream()
                .map(v -> variant(v, promos.get(v.getProduct().getId()), stock))
                .toList();
    }

    private List<ProductVariantResponse> variants(List<ProductVariant> variants, List<Promotion> promos) {
        Map<Long, Integer> stock = stockService.levels(variants.stream().map(ProductVariant::getId).toList());
        return variants.stream().map(v -> variant(v, promos, stock)).toList();
    }

    private static ProductVariantResponse variant(ProductVariant v, List<Promotion> promos, Map<Long, Integer> stock) {
        ProductVariantResponse res = ProductVariantMapper.toResponse(v);
        PricingService.Discounted price = PricingService.best(effectivePrice(v), promos);
        if (price.discounted()) {
            res.setSalePrice(price.salePrice());
            res.setPromotionName(price.promotionName());
            res.setPromotionEndsAt(price.promotionEndsAt());
        }
        res.setStock(stock.get(v.getId()));
        return res;
    }

    public ProductVariantDetailResponse variantDetail(ProductVariant v, List<String> images) {
        ProductVariantDetailResponse res = ProductVariantMapper.toDetailResponse(v);
        List<Promotion> promos = pricingService.productPromotions(List.of(v.getProduct().getId())).get(v.getProduct().getId());
        PricingService.Discounted price = PricingService.best(effectivePrice(v), promos);
        if (price.discounted()) {
            res.setSalePrice(price.salePrice());
        }
        res.setStock(stockService.levels(List.of(v.getId())).get(v.getId()));
        res.setImages(images);
        return res;
    }

    /** Giá của biến thể; biến thể chưa đặt giá thì theo giá cơ bản của sản phẩm (giống lúc tính đơn). */
    static Double effectivePrice(ProductVariant v) {
        return v.getPrice() != null ? v.getPrice() : v.getProduct().getBasePrice();
    }

    /**
     * Còn hàng theo sản phẩm: {@code null} nếu không biến thể nào theo dõi kho; {@code true} nếu có biến thể còn hàng
     * hoặc có biến thể không theo dõi kho (luôn bán được); {@code false} nếu mọi biến thể đều theo dõi kho và đều hết.
     */
    Map<Long, Boolean> inStock(List<Long> productIds) {
        Map<Long, Long> totals = new HashMap<>();
        for (ProductVariantRepository.VariantCount count : productVariantRepository.countByProducts(productIds)) {
            totals.put(count.getProductId(), count.getTotal());
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (InventoryRepository.ProductStock s : inventoryRepository.findProductStock(productIds)) {
            long tracked = s.getTracked() == null ? 0 : s.getTracked();
            long available = s.getAvailable() == null ? 0 : s.getAvailable();
            long total = totals.getOrDefault(s.getProductId(), tracked);
            if (tracked > 0) {
                result.put(s.getProductId(), available > 0 || total > tracked);
            }
        }
        return result;
    }
}
