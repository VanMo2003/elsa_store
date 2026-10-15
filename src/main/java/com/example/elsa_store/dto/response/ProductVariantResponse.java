
package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {
    private Long id;
    private String imageUrl;
    private Long productId;
    private String productName;
    private String color;
    private String size;
    private String sku;
    private Double price;
    private Integer status;
    /** Giá sau khuyến mãi tự động (null nếu không được giảm). */
    private Double salePrice;
    private String promotionName;
    /** Tồn kho hiện tại; null = biến thể không theo dõi kho (vẫn bán được). */
    private Integer stock;
}
