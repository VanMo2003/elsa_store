package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ChatMessageRequest;
import com.example.elsa_store.dto.request.ConversationCreateRequest;
import com.example.elsa_store.dto.response.ChatMessageResponse;
import com.example.elsa_store.dto.response.ChatUnreadResponse;
import com.example.elsa_store.dto.response.ConversationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    /** Khách mở (hoặc mở lại) cuộc trò chuyện với một cửa hàng. */
    ConversationResponse open(ConversationCreateRequest req);

    /** Các cuộc trò chuyện của khách. */
    PageResponse<ConversationResponse> getMine(int page, int size);

    /** Hộp thư chung của cửa hàng người gọi (chủ, quản lý, nhân viên). */
    PageResponse<ConversationResponse> getInbox(int page, int size);

    ConversationResponse getById(Long id);

    /** Mới nhất trước; truyền id tin cũ nhất đang hiện làm {@code beforeId} để tải tiếp tin cũ hơn. */
    List<ChatMessageResponse> getMessages(Long conversationId, Long beforeId, int size);

    ChatMessageResponse send(Long conversationId, ChatMessageRequest req);

    /** Gửi 1–4 ảnh (kèm chú thích tuỳ chọn) thành một tin nhắn. */
    ChatMessageResponse sendImages(Long conversationId, List<MultipartFile> files, String caption);

    ConversationResponse markRead(Long conversationId);

    ChatUnreadResponse countUnread();

    /**
     * Trợ lý AI trả lời thay cửa hàng (nội bộ, không có người gọi). Bỏ qua và trả null khi cuộc trò chuyện đã đi tiếp
     * trong lúc câu trả lời đang được tạo: với {@code explicitAsk} (khách bấm hỏi AI) là khi một nhân viên thật đã trả
     * lời sau tin đó; với tự trả lời là khi có bất kỳ tin nào mới hơn tin {@code replyToMessageId}.
     */
    ChatMessageResponse postAssistantReply(
            Long conversationId,
            Long replyToMessageId,
            String content,
            List<Long> productIds,
            boolean handoff,
            String handoffReason,
            boolean explicitAsk);
}
