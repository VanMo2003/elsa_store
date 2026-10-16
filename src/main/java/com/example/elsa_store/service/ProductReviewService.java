package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ReviewReplyRequest;
import com.example.elsa_store.dto.request.ReviewRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReviewEligibilityResponse;
import com.example.elsa_store.dto.response.ReviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.example.elsa_store.dto.response.ReviewSummaryResponse;

/**
 * Đánh giá sản phẩm. Ai cũng đọc được đánh giá của sản phẩm đang bán; chỉ khách đã nhận hàng (đơn hoàn thành có sản
 * phẩm đó) mới viết được, mỗi khách một đánh giá / sản phẩm; người của cửa hàng trả lời được.
 */
public interface ProductReviewService {

    /** {@code rating} null = mọi số sao. */
    default PageResponse<ReviewResponse> list(Long productId, Integer rating, boolean withImages, int page, int size) {
        return list(productId, rating, withImages, false, page, size);
    }

    /** {@code helpfulFirst} = nhiều lượt "Hữu ích" nhất trước (mặc định mới nhất trước). */
    PageResponse<ReviewResponse> list(Long productId, Integer rating, boolean withImages, boolean helpfulFirst, int page,
                                      int size);

    /** Khách bấm / bỏ "Hữu ích" cho đánh giá của người khác. */
    ReviewResponse markHelpful(Long reviewId, boolean helpful);

    ReviewSummaryResponse summary(Long productId);

    ReviewEligibilityResponse eligibility(Long productId);

    /** Viết mới hoặc sửa đánh giá của chính mình. */
    ReviewResponse upsert(Long productId, ReviewRequest request);

    /** Thêm ảnh vào đánh giá của chính khách cho sản phẩm này (phải viết đánh giá trước), tối đa 5 ảnh. */
    ReviewResponse addImages(Long productId, List<MultipartFile> files);

    /** Gỡ một ảnh khỏi đánh giá của chính khách. */
    ReviewResponse removeImage(Long productId, String url);

    void delete(Long reviewId);

    /** Đánh giá về sản phẩm của cửa hàng người gọi; {@code unreplied} = chỉ những đánh giá chưa trả lời. */
    PageResponse<ReviewResponse> storeReviews(Integer rating, boolean unreplied, int page, int size);

    ReviewResponse reply(Long reviewId, ReviewReplyRequest request);
}
