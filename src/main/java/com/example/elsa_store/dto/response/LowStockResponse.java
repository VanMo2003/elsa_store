package com.example.elsa_store.dto.response;

import lombok.*;

/** Biến thể sắp hết hàng (tồn kho không vượt quá mức tối thiểu). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockResponse {
    private Long productVariantId;
    private String productName;
    private String color;
    private String size;
    private String sku;
    private Integer quantity;
    private Integer minQuantity;
}
