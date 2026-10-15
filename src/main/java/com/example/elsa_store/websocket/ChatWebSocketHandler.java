package com.example.elsa_store.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code /ws/chat}: chỉ một chiều máy chủ → app. Sự kiện là JSON có {@code type}:
 * <ul>
 *   <li>{@code CONNECTED} — ngay sau khi nâng cấp;
 *   <li>{@code MESSAGE} — {@code conversation} (như người nhận thấy) + {@code message};
 *   <li>{@code READ} — {@code conversation} sau khi một phía mở đọc;
 *   <li>{@code PONG} — trả lời nhịp tim {@code {"type":"PING"}} của app.
 * </ul>
 * Tin nhắn gửi bằng {@code POST /conversations/{id}/messages}, không gửi qua socket, để kiểm tra dữ liệu, phân quyền và
 * báo lỗi vẫn đi đường REST bình thường.
 */
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        registry.register(userId, session);
        registry.reply(userId, session, "{\"type\":\"CONNECTED\"}");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (message.getPayload().contains("\"PING\"")) {
            registry.reply(userId(session), session, "{\"type\":\"PONG\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(userId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.unregister(userId(session), session);
    }

    private static Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ChatHandshakeInterceptor.USER_ID);
    }
}
