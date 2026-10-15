package com.example.elsa_store.websocket;

import com.example.elsa_store.config.CustomJwtDecoder;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Xác thực lần nâng cấp lên WebSocket bằng chính access token của REST. Trình duyệt không đặt được header cho
 * WebSocket nên token đi theo {@code ?token=}; header {@code Authorization: Bearer} cũng được. Token đi qua
 * {@link CustomJwtDecoder} nên token đã đăng xuất bị từ chối như ở mọi nơi khác. Tài khoản bị khoá không vào được;
 * ADMIN không có chat.
 */
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID = "userId";

    private final CustomJwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = token(request);
        User user = null;
        if (token != null) {
            try {
                Jwt jwt = jwtDecoder.decode(token);
                user = userRepository.findByUsername(jwt.getSubject()).filter(User::isEnabled).orElse(null);
            } catch (RuntimeException e) {
                user = null;
            }
        }
        if (user == null || user.getRole() == Role.ADMIN) {
            response.setStatusCode(user == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(USER_ID, user.getId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}

    private static String token(ServerHttpRequest request) {
        String query = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (query != null && !query.isBlank()) {
            return query;
        }
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
