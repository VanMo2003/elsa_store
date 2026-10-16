package com.example.elsa_store.dto.response;

/**
 * Lượt xem và chuyển đổi của một sản phẩm trong kỳ: {@code orders} = số đơn (trừ đơn huỷ) đặt trong kỳ có sản phẩm này,
 * {@code conversionRate} = orders / views (0–1).
 */
public record ProductViewStatResponse(
        Long productId,
        String productName,
        String imageUrl,
        long views,
        long orders,
        long unitsSold,
        double conversionRate) {}
