package com.example.elsa_store.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Báo giá ở bước thanh toán: cùng cách tính với lúc tạo đơn (khuyến mãi, mã giảm giá), kèm tồn kho từng dòng để app báo
 * trước khi khách bấm đặt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderQuoteResponse {
    Long storeId;
    String storeName;
    List<Line> items;
    /** Tổng theo giá gốc. */
    double originalSubtotal;
    /** Tổng sau khuyến mãi theo sản phẩm. */
    double subtotal;
    double productDiscount;
    /** Giảm cả đơn (khuyến mãi tự động hoặc mã giảm giá). */
    double orderDiscount;
    /** Mã giảm giá đã áp được (null nếu không dùng mã). */
    String couponCode;
    String orderPromotionName;
    double totalDiscount;
    double total;
    String discountNote;
    /** Có dòng vượt tồn kho — app khoá nút đặt hàng. */
    boolean stockProblem;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Line {
        Long productVariantId;
        Long productId;
        String productName;
        String color;
        String size;
        String imageUrl;
        int quantity;
        double originalUnitPrice;
        double unitPrice;
        double discount;
        double lineTotal;
        String promotionName;
        /** Tồn kho hiện tại; null = biến thể không theo dõi kho. */
        Integer availableStock;
        boolean insufficientStock;
    }
}
