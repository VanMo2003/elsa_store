package com.example.elsa_store.config;

import com.example.elsa_store.websocket.ChatHandshakeInterceptor;
import com.example.elsa_store.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** WebSocket thuần (không STOMP) tại {@code <context-path>/ws/chat?token=<access token>}. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Access token xác thực socket (không dùng cookie) nên mọi origin đều được nối, giống CORS của REST.
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * Đóng socket im lặng quá 70 giây (app gửi nhịp tim mỗi 25 giây). Điện thoại mất mạng hoặc bị tắt app không gửi
     * khung đóng; nếu không có bước này socket của nó vẫn "online" và push chat bị bỏ qua.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(70_000L);
        return container;
    }
}
