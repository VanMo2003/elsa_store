package com.example.elsa_store.dto.response;

/**
 * Gợi ý nhập hàng cho một phân loại đang bán chạy so với tồn kho.
 *
 * @param soldInWindow số đã bán trong {@code days} ngày qua (đơn đặt trong kỳ, trừ đơn huỷ)
 * @param perDay bán trung bình mỗi ngày (làm tròn 1 chữ số)
 * @param daysLeft ước tính còn bao nhiêu ngày thì hết (0 = đã hết)
 * @param suggestedQuantity nên nhập thêm để đủ bán {@code cover} ngày
 * @param estimatedCost tiền nhập ước tính theo giá vốn hiện tại (null khi chưa có giá vốn)
 */
public record RestockSuggestionResponse(
        Long productId,
        Long productVariantId,
        String productName,
        String imageUrl,
        String color,
        String size,
        String sku,
        int quantity,
        long soldInWindow,
        double perDay,
        int daysLeft,
        int suggestedQuantity,
        Double costPrice,
        Double estimatedCost) {}
