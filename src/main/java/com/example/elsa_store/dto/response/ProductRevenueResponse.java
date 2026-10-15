package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRevenueResponse {
    private Long productVariantId;
    private String productName;
    private String imageUrl;
    private Long quantitySold;
    private Double revenue;
    /** Giá vốn các dòng đã có giá vốn. */
    private Double costOfGoods;
    /** Lợi nhuận gộp; null khi còn đơn vị bán ra chưa có giá vốn. */
    private Double profit;
    private Long uncostedQuantity;
}
