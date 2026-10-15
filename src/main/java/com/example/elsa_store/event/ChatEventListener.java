package com.example.elsa_store.event;

import com.example.elsa_store.ai.ChatAssistant;
import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.dto.response.ConversationResponse;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import com.example.elsa_store.websocket.ChatSessionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển thay đổi của chat tới người nhận sau khi đã commit: mọi socket đang mở của cả hai phía nhận sự kiện (nên thiết
 * bị thứ hai hay nhân viên khác cũng thấy), người phía bên kia không có socket được push. Chạy ngay trên luồng request
 * (không {@code @Async}) để hai tin gửi liên tiếp tới socket đúng thứ tự; bản thân việc push thì bất đồng bộ
 * ({@link PushNotificationListener}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final PushNotificationListener pushNotificationListener;
    private final ChatAssistant chatAssistant;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatUpdated(ChatUpdatedEvent event) {
        registry.send(List.of(event.customerUserId()), payload(event, event.customerView()));
        registry.send(event.storeUserIds(), payload(event, event.storeView()));
        if (event.type() != ChatUpdatedEvent.Type.MESSAGE) {
            return;
        }
        push(event);
        if (event.message().getSenderSide() == ChatSide.CUSTOMER) {
            Long conversationId = event.customerView().getId();
            Long messageId = event.message().getId();
            if (event.askAssistant()) {
                chatAssistant.askNow(conversationId, messageId);
            } else {
                // Trả lời muộn hơn nếu cửa hàng bật tự trả lời và trong lúc đó chưa ai trả lời.
                chatAssistant.onCustomerMessage(conversationId, messageId);
            }
        }
    }

    private void push(ChatUpdatedEvent event) {
        boolean fromCustomer = event.message().getSenderSide() == ChatSide.CUSTOMER;
        List<Long> audience = fromCustomer ? event.storeUserIds() : List.of(event.customerUserId());
        List<Long> recipients = audience.stream()
                .filter(id -> !id.equals(event.senderUserId()) && !registry.isOnline(id))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        ConversationResponse conversation = event.customerView();
        String content = pushText(event);
        NotificationMessages.Message message = fromCustomer
                ? NotificationMessages.chatToStore(conversation.getCustomerName(), content)
                : NotificationMessages.chatToCustomer(conversation.getStoreName(), content);
        Map<String, String> data = new HashMap<>();
        data.put("type", "CHAT_MESSAGE");
        data.put("link", AppLinks.chat(conversation.getId()));
        data.put("referenceId", conversation.getId().toString());
        // Mỗi cuộc trò chuyện một thông báo trên Android: tin mới thay tin cũ.
        data.put("tag", "chat-" + conversation.getId());
        // Gọi trực tiếp thay vì publish: sự kiện phát từ callback sau commit sẽ đăng ký vào transaction đã sắp kết thúc
        // và không bao giờ chạy. Lời gọi vẫn đi qua proxy @Async.
        pushNotificationListener.onPushRequested(
                new PushRequestedEvent(recipients, message.title(), message.body(), data));
    }

    /** Tin chỉ có thẻ sản phẩm vẫn cần một dòng chữ để hiện trong thông báo. */
    private static String pushText(ChatUpdatedEvent event) {
        String content = event.message().getContent();
        boolean hasProducts = event.message().getProducts() != null
                && !event.message().getProducts().isEmpty();
        return content == null || content.isBlank() ? (hasProducts ? "Đã gửi sản phẩm" : "Tin nhắn mới") : content;
    }

    private String payload(ChatUpdatedEvent event, ConversationResponse view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", event.type().name());
        body.put("conversation", view);
        if (event.message() != null) {
            body.put("message", event.message());
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize chat event", e);
            return "{\"type\":\"" + event.type().name() + "\"}";
        }
    }
}
