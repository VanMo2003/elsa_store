
package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailResponse {

    private Long id;
    private String imageUrl;
    private String name;
    private String description;
    private Double basePrice;
    private String categoryName;
    private Long categoryId;
    private Long storeId;
    private String storeName;
    /** Phí giao của cửa hàng (0 = miễn phí giao hàng). */
    private Double shippingFee;
    /** Mức tiền hàng được miễn phí giao; null = không có. */
    private Double freeShippingThreshold;
    /** Cửa hàng đang tạm nghỉ bán (khách không đặt được) và lời nhắn. */
    private Boolean storePaused;
    private String storePauseNote;
    private Integer status;
    private List<String> images;
    private List<ProductVariantResponse> productVariants;
    /** Giá sau khuyến mãi tự động của giá cơ bản (null nếu không được giảm). */
    private Double salePrice;
    private Integer discountPercent;
    private String promotionName;
    /** Lúc khuyến mãi đang áp dụng kết thúc (00:00 ngày sau ngày cuối); null = không giới hạn hoặc không giảm. */
    private LocalDateTime promotionEndsAt;
    /** false = mọi biến thể đều hết hàng; null = chưa theo dõi kho. */
    private Boolean inStock;

    /** Điểm đánh giá trung bình (một chữ số thập phân); null khi chưa có đánh giá. */
    private Double ratingAverage;

    private Long ratingCount;
    /** Số món đã bán (đơn hoàn thành). */
    private Long soldCount;
}
