package com.example.elsa_store.mapper;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.dto.response.ChatMessageResponse;
import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.dto.response.ConversationResponse;
import com.example.elsa_store.entity.ChatMessage;
import com.example.elsa_store.entity.Conversation;
import com.example.elsa_store.entity.User;

import java.util.List;
import java.util.Map;

public final class ChatMapper {

    private ChatMapper() {}

    /** Gọi trong transaction: đọc các quan hệ lazy của tin nhắn. */
    public static ChatMessageResponse toMessageResponse(ChatMessage message, Map<Long, ChatProductResponse> products) {
        List<ChatProductResponse> attached = message.productIdList().stream()
                .map(products::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderSide(message.getSenderSide())
                .senderUserId(message.getSenderUserId())
                .senderName(message.getSenderName())
                .senderRole(message.getSenderRole())
                .content(message.getContent())
                .aiGenerated(message.getAiGenerated())
                .products(attached.isEmpty() ? null : attached)
                .createdAt(message.getCreatedAt())
                .build();
    }

    /** Cuộc trò chuyện nhìn từ {@code side}: đếm chưa đọc của phía mình, ghi chú chuyển giao chỉ cửa hàng thấy. */
    public static ConversationResponse forSide(Conversation conversation, ChatSide side) {
        boolean customerView = side == ChatSide.CUSTOMER;
        var store = conversation.getStore();
        var customer = conversation.getCustomer();
        return ConversationResponse.builder()
                .id(conversation.getId())
                .storeId(store.getId())
                .storeName(store.getName())
                .storeAddress(store.getAddress())
                .storePhone(store.getPhone())
                .storeImageUrl(store.getImageUrl())
                .customerId(customer.getId())
                .customerName(displayName(customer))
                .customerPhone(customer.getPhone())
                .lastMessage(conversation.getLastMessage())
                .lastSenderSide(conversation.getLastSenderSide())
                .lastMessageAt(conversation.getLastMessageAt())
                .mySide(side)
                .unread(customerView ? conversation.getCustomerUnread() : conversation.getStoreUnread())
                .needsStaff(customerView ? null : conversation.getNeedsStaff())
                .handoffReason(customerView ? null : conversation.getHandoffReason())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    public static String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }
}
