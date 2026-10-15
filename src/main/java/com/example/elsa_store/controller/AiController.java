package com.example.elsa_store.controller;

import com.example.elsa_store.ai.AiClient;
import com.example.elsa_store.ai.ChatAssistant;
import com.example.elsa_store.ai.ShoppingAssistant;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AiAskRequest;
import com.example.elsa_store.dto.request.AiStoreSettingsRequest;
import com.example.elsa_store.dto.response.AiStoreSettingsResponse;
import com.example.elsa_store.dto.response.AiSuggestionResponse;
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
