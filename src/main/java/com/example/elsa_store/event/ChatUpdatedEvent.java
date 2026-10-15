package com.example.elsa_store.event;

import com.example.elsa_store.dto.response.ChatMessageResponse;
import com.example.elsa_store.dto.response.ConversationResponse;

import java.util.List;

/**
 * Một cuộc trò chuyện vừa đổi (tin mới, hoặc một phía vừa mở đọc). Dựng trong transaction với đủ dữ liệu cho việc đẩy
 * WebSocket và push, nên listener chạy sau commit không phải đụng tới entity lazy.
 *
 * @param message trống với {@link Type#READ}
 * @param customerUserId tài khoản của khách
 * @param storeUserIds chủ, quản lý và nhân viên của cửa hàng
 * @param senderUserId tài khoản gây ra thay đổi (không bao giờ bị push); trống với trợ lý AI
 * @param askAssistant khách yêu cầu trợ lý AI trả lời tin này ngay
 */
public record ChatUpdatedEvent(
        Type type,
        ChatMessageResponse message,
        ConversationResponse customerView,
        ConversationResponse storeView,
        Long customerUserId,
        List<Long> storeUserIds,
        Long senderUserId,
        boolean askAssistant) {

    public enum Type {
        MESSAGE,
        READ
    }
}
