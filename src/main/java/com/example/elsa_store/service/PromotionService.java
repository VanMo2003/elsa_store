package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.PromotionRequest;
import com.example.elsa_store.dto.response.PromotionResponse;

import java.util.List;

public interface PromotionService {
    PromotionResponse create(PromotionRequest req);

    PromotionResponse update(Long id, PromotionRequest req);

    void delete(Long id);

    PromotionResponse getById(Long id);

    List<PromotionResponse> getAll();

    /** Mã giảm giá đang chạy của một cửa hàng — khách xem để chọn ở bước thanh toán. */
    List<PromotionResponse> activeCoupons(Long storeId);
}
