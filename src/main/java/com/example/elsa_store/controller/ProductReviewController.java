package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ReviewReplyRequest;
import com.example.elsa_store.dto.request.ReviewRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReviewEligibilityResponse;
import com.example.elsa_store.dto.response.ReviewResponse;
import com.example.elsa_store.dto.response.ReviewSummaryResponse;
import com.example.elsa_store.service.ProductReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Đánh giá sản phẩm: đọc công khai; khách đã nhận hàng viết / sửa / xoá; người của cửa hàng xem và trả lời. */
@RestController
public class ProductReviewController {

    private final ProductReviewService reviewService;

    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> list(@PathVariable Long productId,
                                                          @RequestParam(required = false) Integer rating,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reviewService.list(productId, rating, page, size));
    }

    @GetMapping("/products/{productId}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> summary(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.summary(productId));
    }

    /** Người đang xem viết được đánh giá không (cần đăng nhập để biết; chưa đăng nhập trả NOT_SIGNED_IN). */
    @GetMapping("/products/{productId}/reviews/me")
    public ApiResponse<ReviewEligibilityResponse> eligibility(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.eligibility(productId));
    }

    @PostMapping("/products/{productId}/reviews")
    public ApiResponse<ReviewResponse> upsert(@PathVariable Long productId, @Valid @RequestBody ReviewRequest body) {
        return ApiResponse.ok(reviewService.upsert(productId, body));
    }

    @DeleteMapping("/reviews/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/reviews/store")
    public ApiResponse<PageResponse<ReviewResponse>> storeReviews(@RequestParam(required = false) Integer rating,
                                                                  @RequestParam(defaultValue = "false") boolean unreplied,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.storeReviews(rating, unreplied, page, size));
    }

    @PutMapping("/reviews/{id}/reply")
    public ApiResponse<ReviewResponse> reply(@PathVariable Long id, @Valid @RequestBody ReviewReplyRequest body) {
        return ApiResponse.ok(reviewService.reply(id, body));
    }
}
