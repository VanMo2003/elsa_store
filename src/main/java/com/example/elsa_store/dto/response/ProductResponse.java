
package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String imageUrl;
    private String name;
    private String description;
    private Double basePrice;
    private String categoryName;
    private Long categoryId;
    private Long storeId;
    private String storeName;
    /** 1: đang bán, 0: ẩn. */
    private Integer status;
    /** Giá sau khuyến mãi tự động (null nếu không được giảm). */
    private Double salePrice;
    /** Phần trăm giảm làm tròn, để hiện nhãn "-20%" (null nếu không được giảm). */
    private Integer discountPercent;
    private String promotionName;
    /** Lúc khuyến mãi đang áp dụng kết thúc (00:00 ngày sau ngày cuối); null = không giới hạn hoặc không giảm. */
    private LocalDateTime promotionEndsAt;
    /** Còn hàng không: false = mọi biến thể đều hết; null = chưa theo dõi kho. */
    private Boolean inStock;

    /** Điểm đánh giá trung bình (một chữ số thập phân); null khi chưa có đánh giá. */
    private Double ratingAverage;

    private Long ratingCount;
}
