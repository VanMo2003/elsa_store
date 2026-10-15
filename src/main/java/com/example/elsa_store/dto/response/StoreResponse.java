
package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ApprovalStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreResponse {
    Long id;
    String name;
    String description;
    String imageUrl;
    String ownerName;
    String email;
    String phone;
    String address;
    ApprovalStatus approvalStatus;
    String rejectionReason;
    LocalDateTime submittedAt;
    LocalDateTime reviewedAt;
    Long ownerId;
    String ownerUsername;
    /** Phí giao mỗi đơn; 0 = miễn phí giao hàng. */
    Double shippingFee;
    /** Mức tiền hàng được miễn phí giao; trống = không có. */
    Double freeShippingThreshold;
    /** Trang cửa hàng công khai: điểm đánh giá trung bình (một chữ số thập phân) trên mọi sản phẩm, trống khi chưa có. */
    Double ratingAverage;
    Long ratingCount;
    /** Số sản phẩm đang bán. */
    Long productCount;
    /** Tổng số món đã giao xong. */
    Long soldCount;
}
