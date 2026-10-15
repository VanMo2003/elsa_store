package com.example.elsa_store.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Các socket chat đang mở của từng tài khoản (một tài khoản có thể đăng nhập trên điện thoại và trình duyệt cùng lúc).
 * Phiên được bọc trong {@link ConcurrentWebSocketSessionDecorator} vì một tin có thể được đẩy từ nhiều luồng request
 * cùng lúc mà session thô không cho gửi song song. Chỉ chạy một instance: chạy nhiều máy chủ thì cần message broker
 * dùng chung.
 */
@Slf4j
@Component
public class ChatSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .put(
                        session.getId(),
                        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, open) -> {
            open.remove(session.getId());
            return open.isEmpty() ? null : open;
        });
    }

    /** Đang mở app ngay lúc này — dùng để bỏ qua push cho người đang nhìn thấy cuộc trò chuyện. */
    public boolean isOnline(Long userId) {
        // Socket đã đứt (mất mạng) có thể còn nằm trong danh sách tới khi callback đóng chạy.
        return userId != null
                && sessions.getOrDefault(userId, Map.of()).values().stream().anyMatch(WebSocketSession::isOpen);
    }

    public void send(Collection<Long> userIds, String payload) {
        for (Long userId : userIds) {
            if (userId != null) {
                sessions.getOrDefault(userId, Map.of()).values().forEach(session -> sendTo(session, payload));
            }
        }
    }

    /** Trả lời trên đúng một socket (nhịp tim), qua cùng lớp bọc an toàn luồng. */
    public void reply(Long userId, WebSocketSession session, String payload) {
        WebSocketSession wrapped = sessions.getOrDefault(userId, Map.of()).get(session.getId());
        if (wrapped != null) {
            sendTo(wrapped, payload);
        }
    }

    private void sendTo(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping chat socket {}: {}", session.getId(), e.getMessage());
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
                // đã đóng
            }
        }
    }
}
