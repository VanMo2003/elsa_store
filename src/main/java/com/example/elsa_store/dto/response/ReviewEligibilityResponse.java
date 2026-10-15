package com.example.elsa_store.dto.response;

import lombok.*;

/** Người đang xem có viết được đánh giá cho sản phẩm không, và đánh giá họ đã viết (nếu có). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewEligibilityResponse {
    private Boolean canReview;
    /** NOT_SIGNED_IN, NOT_CUSTOMER, NOT_PURCHASED — khi không viết được. */
    private String reason;
    private ReviewResponse review;
}
