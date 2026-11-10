package com.example.elsa_store.dto.response;

import java.time.LocalDateTime;

/**
 * Một phân loại còn tồn kho nhưng không bán được trong {@code days} ngày gần nhất.
 *
 * @param lastSoldAt lần gần nhất có đơn (trừ đơn huỷ) đặt phân loại này; null = chưa bán lần nào
 * @param daysSinceLastSale số ngày kể từ lần bán đó; null khi chưa bán lần nào
 * @param stockValue vốn đang nằm trong kho = tồn × giá vốn (0 khi chưa có giá vốn)
 * @param hidden sản phẩm đang ẩn với khách (cửa hàng tự ẩn)
 */
public record SlowMoverResponse(
        Long productId,
        Long productVariantId,
        String productName,
        String imageUrl,
        String color,
        String size,
        String sku,
        int quantity,
        Double costPrice,
        double stockValue,
        LocalDateTime lastSoldAt,
        Long daysSinceLastSale,
        boolean hidden) {}
