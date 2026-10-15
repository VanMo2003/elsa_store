
package com.example.elsa_store.dto.response;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantDetailResponse {
    private Long id;
    private String imageUrl;
    private Long productId;
    private String productName;
    private String color;
    private String size;
    private String sku;
    private Double price;
    private Integer status;
    private List<String> images = new ArrayList<>();
    /** Giá sau khuyến mãi tự động (null nếu không được giảm). */
    private Double salePrice;
    /** Tồn kho hiện tại; null = không theo dõi kho. */
    private Integer stock;
}
