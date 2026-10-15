package com.example.elsa_store.dto.response;

import lombok.*;

/** Một dòng trong màn kho hàng: biến thể và tồn kho của nó. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItemResponse {
    private Long productVariantId;
    private Long productId;
    private String productName;
    private String color;
    private String size;
    private String sku;
    private String imageUrl;
    /** Giá bán. */
    private Double price;
    /** Giá vốn bình quân; null = chưa có. */
    private Double costPrice;
    /** false = chưa theo dõi kho (bán không giới hạn). */
    private Boolean tracked;
    private Integer quantity;
    private Integer minQuantity;
    /** OK, LOW (sắp hết), OUT (hết hàng) hoặc UNTRACKED. */
    private String status;
    /** Số lượng × giá vốn; null khi chưa theo dõi kho hoặc chưa có giá vốn. */
    private Double stockValue;
}
