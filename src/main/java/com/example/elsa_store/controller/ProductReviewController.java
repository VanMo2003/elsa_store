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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
                                                          @RequestParam(defaultValue = "false") boolean withImages,
                                                          @RequestParam(defaultValue = "newest") String sort,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reviewService.list(productId, rating, withImages, "helpful".equalsIgnoreCase(sort), page,
                size));
    }

    /** Khách bấm "Hữu ích" cho đánh giá của người khác (bấm lại không đếm thêm). */
    @PostMapping("/reviews/{id}/helpful")
    public ApiResponse<ReviewResponse> helpful(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.markHelpful(id, true));
    }

    @DeleteMapping("/reviews/{id}/helpful")
    public ApiResponse<ReviewResponse> notHelpful(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.markHelpful(id, false));
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

    /** Thêm ảnh vào đánh giá của mình (multipart, phần {@code files}); viết đánh giá trước rồi mới thêm ảnh. */
    @PostMapping(value = "/products/{productId}/reviews/me/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponse> addImages(@PathVariable Long productId,
                                                 @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(reviewService.addImages(productId, files));
    }

    @DeleteMapping("/products/{productId}/reviews/me/images")
    public ApiResponse<ReviewResponse> removeImage(@PathVariable Long productId, @RequestParam String url) {
        return ApiResponse.ok(reviewService.removeImage(productId, url));
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
