package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ChatMessageRequest;
import com.example.elsa_store.dto.request.ConversationCreateRequest;
import com.example.elsa_store.dto.response.ChatMessageResponse;
import com.example.elsa_store.dto.response.ChatUnreadResponse;
import com.example.elsa_store.dto.response.ConversationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chat khách ↔ cửa hàng qua REST. Phần nhận tin tức thì của cùng dữ liệu đi qua WebSocket {@code /ws/chat}
 * (xem {@code ChatWebSocketHandler}); app gửi ở đây và nghe ở đó.
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Chỉ khách: trả về cuộc trò chuyện sẵn có với cửa hàng đó, hoặc mở mới. */
    @PostMapping
    public ApiResponse<ConversationResponse> open(@Valid @RequestBody ConversationCreateRequest req) {
        return ApiResponse.ok(chatService.open(req));
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<ConversationResponse>> getMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(chatService.getMine(page, size));
    }

    /** Chủ, quản lý hoặc nhân viên: hộp thư chung của cửa hàng mình. */
    @GetMapping("/inbox")
    public ApiResponse<PageResponse<ConversationResponse>> getInbox(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(chatService.getInbox(page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<ChatUnreadResponse> countUnread() {
        return ApiResponse.ok(chatService.countUnread());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(chatService.getById(id));
    }

    /** Mới nhất trước. Tải tin cũ hơn bằng {@code beforeId} = id của tin cũ nhất đã có. */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(chatService.getMessages(id, beforeId, size));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ChatMessageResponse> send(@PathVariable Long id, @Valid @RequestBody ChatMessageRequest req) {
        return ApiResponse.ok(chatService.send(id, req));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<ConversationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(chatService.markRead(id));
    }
}
