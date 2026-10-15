package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.PromotionProductRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionProductResponse;

import java.util.Collection;
import java.util.List;

/** Sản phẩm được áp một khuyến mãi — chỉ chủ / quản lý của cửa hàng sở hữu khuyến mãi. */
public interface PromotionProductService {
    PromotionProductResponse create(PromotionProductRequest req);

    /** Các sản phẩm đang gắn với khuyến mãi. */
    List<ProductResponse> products(Long promotionId);

    /** Thay toàn bộ danh sách sản phẩm của khuyến mãi; rỗng = khuyến mãi áp cho cả đơn. */
    List<ProductResponse> setProducts(Long promotionId, Collection<Long> productIds);

    void delete(Long promotionProductId);
}
