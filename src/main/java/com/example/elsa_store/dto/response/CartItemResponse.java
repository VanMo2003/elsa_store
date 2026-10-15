package com.example.elsa_store.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private Long productVariantId;
    private Long productId;

    private String productName;
    private String color;
    private String size;
    private String sku;
    private String imageUrl;

    /** Đơn giá hiện tại (đã trừ khuyến mãi theo sản phẩm). */
    private Double unitPrice;
    /** Đơn giá gốc — khác {@link #unitPrice} khi sản phẩm đang được giảm giá. */
    private Double originalUnitPrice;
    private String promotionName;
    private Integer quantity;
    private Double lineTotal;
    /** Tồn kho hiện tại; null = không theo dõi kho. */
    private Integer availableStock;
    /** Sản phẩm đã ngừng bán / cửa hàng đóng — app nhắc khách bỏ khỏi giỏ. */
    private boolean unavailable;
}
