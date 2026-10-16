package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer rating;
    private String comment;
    private String variantLabel;
    /** Tên hiển thị của khách (tên đăng nhập được che bớt khi không có họ tên). */
    private String authorName;
    /** Ảnh đại diện của người viết. */
    private String authorAvatarUrl;
    private LocalDateTime createdAt;
    /** Khách đã sửa sau khi đăng. */
    private Boolean edited;
    private String storeReply;
    private LocalDateTime repliedAt;
    /** Đánh giá của chính người đang xem. */
    private Boolean mine;
    /** Ảnh khách đính kèm (đường dẫn /uploads/reviews/...). */
    private List<String> images;
    /** Xu vừa được thưởng ở lần gọi này (chỉ có trong phản hồi viết đánh giá / thêm ảnh). */
    private Integer rewardCoins;
}
