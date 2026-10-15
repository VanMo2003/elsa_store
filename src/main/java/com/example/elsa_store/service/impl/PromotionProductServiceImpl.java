package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.PromotionProductRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.PromotionProduct;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.ProductMapper;
import com.example.elsa_store.mapper.PromotionMapper;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.PromotionProductRepository;
import com.example.elsa_store.repository.PromotionRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.PromotionProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gắn sản phẩm vào khuyến mãi. Khuyến mãi gắn sản phẩm thì chỉ giảm giá cho các sản phẩm đó (xem {@link PricingService});
 * mọi thao tác đều kiểm tra người gọi là chủ / quản lý của cửa hàng sở hữu khuyến mãi, và sản phẩm cùng cửa hàng.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PromotionProductServiceImpl implements PromotionProductService {

    static final int MAX_PRODUCTS = 200;

    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final AccessGuard accessGuard;

    @Override
    public PromotionProductResponse create(PromotionProductRequest req) {
        Promotion promotion = writablePromotion(req.getPromotionId());
        Product product = sameStoreProduct(promotion, req.getProductId());
        if (!promotionProductRepository.existsByPromotion_IdAndProduct_Id(promotion.getId(), product.getId())) {
            PromotionProduct link = new PromotionProduct();
            link.setProduct(product);
            link.setPromotion(promotion);
            promotionProductRepository.save(link);
        }
        PromotionProductResponse response = new PromotionProductResponse();
        response.setProduct(ProductMapper.toResponse(product));
        response.setPromotion(PromotionMapper.toResponse(promotion));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> products(Long promotionId) {
        Promotion promotion = findPromotion(promotionId);
        accessGuard.authorize(promotion.getStore(), Access.MANAGER_READ);
        return promotionProductRepository.findAllByPromotion_Id(promotionId).stream()
                .map(pp -> ProductMapper.toResponse(pp.getProduct()))
                .toList();
    }

    @Override
    public List<ProductResponse> setProducts(Long promotionId, Collection<Long> productIds) {
        Promotion promotion = writablePromotion(promotionId);
        Set<Long> wanted = new LinkedHashSet<>(productIds == null ? List.of() : productIds);
        if (wanted.size() > MAX_PRODUCTS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Too many products in one promotion");
        }
        List<Product> products = wanted.stream().map(id -> sameStoreProduct(promotion, id)).toList();
        promotionProductRepository.deleteAllByPromotion_Id(promotionId);
        promotionProductRepository.flush();
        for (Product product : products) {
            PromotionProduct link = new PromotionProduct();
            link.setProduct(product);
            link.setPromotion(promotion);
            promotionProductRepository.save(link);
        }
        return products.stream().map(ProductMapper::toResponse).toList();
    }

    @Override
    public void delete(Long promotionProductId) {
        PromotionProduct link = promotionProductRepository.findById(promotionProductId)
                .orElseThrow(() -> new ResourceNotFoundException("PromotionProduct not found"));
        accessGuard.authorize(link.getPromotion().getStore(), Access.MANAGER_WRITE);
        promotionProductRepository.delete(link);
    }

    private Promotion findPromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
    }

    private Promotion writablePromotion(Long id) {
        Promotion promotion = findPromotion(id);
        accessGuard.authorize(promotion.getStore(), Access.MANAGER_WRITE);
        return promotion;
    }

    private Product sameStoreProduct(Promotion promotion, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStore() == null || promotion.getStore() == null
                || !product.getStore().getId().equals(promotion.getStore().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product and promotion must belong to the same store");
        }
        return product;
    }
}
