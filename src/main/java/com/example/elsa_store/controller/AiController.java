package com.example.elsa_store.controller;

import com.example.elsa_store.ai.AiClient;
import com.example.elsa_store.ai.ChatAssistant;
import com.example.elsa_store.ai.ProductCopywriter;
import com.example.elsa_store.ai.QuestionAnswerWriter;
import com.example.elsa_store.ai.ReportAnalyst;
import com.example.elsa_store.ai.ReviewDigestWriter;
import com.example.elsa_store.ai.ReviewReplyWriter;
import com.example.elsa_store.ai.ShoppingAssistant;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AiAskRequest;
import com.example.elsa_store.dto.request.AiStoreSettingsRequest;
import com.example.elsa_store.dto.request.ProductDescriptionRequest;
import com.example.elsa_store.dto.request.ReportInsightRequest;
import com.example.elsa_store.dto.response.AiStoreSettingsResponse;
import com.example.elsa_store.dto.response.AiDraftResponse;
import com.example.elsa_store.dto.response.AiSuggestionResponse;
import com.example.elsa_store.dto.response.ProductDescriptionResponse;
import com.example.elsa_store.dto.response.ReportInsightResponse;
import com.example.elsa_store.dto.response.ReviewDigestResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Tính năng AI (Google Gemini, Claude hoặc máy chủ chuẩn OpenAI khác — xem {@link AiClient}). Mọi lời gọi đi qua máy
 * chủ: app không bao giờ giữ khoá API.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiClient aiClient;
    private final ChatAssistant chatAssistant;
    private final ShoppingAssistant shoppingAssistant;
    private final ReportAnalyst reportAnalyst;
    private final ProductCopywriter productCopywriter;
    private final ReviewReplyWriter reviewReplyWriter;
    private final QuestionAnswerWriter questionAnswerWriter;
    private final ReviewDigestWriter reviewDigestWriter;

    /** Công khai: để app ẩn các nút AI khi máy chủ chưa có khoá API. */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "enabled", aiClient.isEnabled(), "provider", aiClient.getProvider().name().toLowerCase()));
    }

    /**
     * Trợ lý mua sắm Elsa: hỏi một câu, nhận lời tư vấn kèm thẻ sản phẩm. Công khai như phần duyệt cửa hàng; giới hạn lượt
     * theo tài khoản, hoặc theo IP khi chưa đăng nhập.
     */
    @PostMapping("/ask")
    public ApiResponse<AiSuggestionResponse> ask(
            @Valid @RequestBody AiAskRequest req, Principal principal, HttpServletRequest http) {
        String client = principal != null ? "user:" + principal.getName() : "ip:" + http.getRemoteAddr();
        return ApiResponse.ok(shoppingAssistant.ask(req, client));
    }

    /** Chủ / quản lý / nhân viên: bản nháp trả lời để duyệt rồi gửi (không có gì tới khách). */
    @PostMapping("/conversations/{id}/suggest-reply")
    public ApiResponse<AiSuggestionResponse> suggestReply(@PathVariable Long id) {
        return ApiResponse.ok(chatAssistant.suggest(id));
    }

    /**
     * Nhận xét báo cáo của kỳ: tóm tắt, điểm nổi bật, việc nên làm. Quyền như xem báo cáo (chủ / quản lý; ADMIN toàn sàn
     * hoặc storeId). Chưa bật AI, AI bận hay hết lượt thì trả nhận xét theo quy tắc ({@code ai=false} kèm {@code notice}).
     */
    @PostMapping("/report-insights")
    public ApiResponse<ReportInsightResponse> reportInsights(@Valid @RequestBody ReportInsightRequest req) {
        return ApiResponse.ok(reportAnalyst.analyse(req.from(), req.to(), req.storeId()));
    }

    /**
     * Thành viên cửa hàng: bản nháp mô tả sản phẩm từ tên, danh mục, màu / cỡ (khi sửa sản phẩm có sẵn) và gợi ý đã gõ. Chưa
     * bật AI, AI bận hay hết lượt thì trả mẫu soạn sẵn ({@code ai=false} kèm {@code notice}).
     */
    @PostMapping("/product-description")
    public ApiResponse<ProductDescriptionResponse> productDescription(@Valid @RequestBody ProductDescriptionRequest req) {
        return ApiResponse.ok(productCopywriter.write(req));
    }

    /**
     * Chủ / quản lý / nhân viên: bản nháp phản hồi một đánh giá của khách (không gửi gì tới khách). Chưa bật AI, AI bận hay
     * hết lượt thì trả mẫu soạn sẵn theo số sao ({@code ai=false} kèm {@code notice}).
     */
    @PostMapping("/reviews/{id}/suggest-reply")
    public ApiResponse<AiDraftResponse> suggestReviewReply(@PathVariable Long id) {
        return ApiResponse.ok(reviewReplyWriter.suggest(id));
    }

    /**
     * Chủ / quản lý / nhân viên: bản nháp trả lời câu hỏi của khách, chỉ dựa trên hồ sơ thật của sản phẩm. Chưa bật AI, AI
     * bận hay hết lượt thì trả mẫu soạn sẵn nêu giá, màu, cỡ ({@code ai=false} kèm {@code notice}).
     */
    @PostMapping("/questions/{id}/suggest-answer")
    public ApiResponse<AiDraftResponse> suggestQuestionAnswer(@PathVariable Long id) {
        return ApiResponse.ok(questionAnswerWriter.suggest(id));
    }

    /**
     * Công khai: "Khách nói gì" — tóm tắt đánh giá của sản phẩm do AI viết (cần ít nhất 3 đánh giá có nhận xét). Lưu lại và
     * chỉ viết lại khi có đánh giá mới / sửa / xoá; {@code data=null} khi chưa có bản tóm tắt nào (app ẩn mục này).
     */
    @GetMapping("/products/{productId}/review-digest")
    public ApiResponse<ReviewDigestResponse> reviewDigest(@PathVariable Long productId) {
        return ApiResponse.ok(reviewDigestWriter.digest(productId));
    }

    @GetMapping("/store-settings")
    public ApiResponse<AiStoreSettingsResponse> settings() {
        return ApiResponse.ok(chatAssistant.settings());
    }

    /** Chủ hoặc quản lý bật / tắt tự trả lời của trợ lý. */
    @PutMapping("/store-settings")
    public ApiResponse<AiStoreSettingsResponse> updateSettings(@Valid @RequestBody AiStoreSettingsRequest req) {
        return ApiResponse.ok(chatAssistant.updateSettings(req));
    }
}
