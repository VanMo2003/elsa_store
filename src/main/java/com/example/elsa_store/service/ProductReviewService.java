package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ReviewReplyRequest;
import com.example.elsa_store.dto.request.ReviewRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReviewEligibilityResponse;
import com.example.elsa_store.dto.response.ReviewResponse;
import com.example.elsa_store.dto.response.ReviewSummaryResponse;

/**
 * Đánh giá sản phẩm. Ai cũng đọc được đánh giá của sản phẩm đang bán; chỉ khách đã nhận hàng (đơn hoàn thành có sản
 * phẩm đó) mới viết được, mỗi khách một đánh giá / sản phẩm; người của cửa hàng trả lời được.
 */
public interface ProductReviewService {

    /** {@code rating} null = mọi số sao. */
    PageResponse<ReviewResponse> list(Long productId, Integer rating, int page, int size);

    ReviewSummaryResponse summary(Long productId);

    ReviewEligibilityResponse eligibility(Long productId);

    /** Viết mới hoặc sửa đánh giá của chính mình. */
    ReviewResponse upsert(Long productId, ReviewRequest request);

    void delete(Long reviewId);

    /** Đánh giá về sản phẩm của cửa hàng người gọi; {@code unreplied} = chỉ những đánh giá chưa trả lời. */
    PageResponse<ReviewResponse> storeReviews(Integer rating, boolean unreplied, int page, int size);

    ReviewResponse reply(Long reviewId, ReviewReplyRequest request);
}
