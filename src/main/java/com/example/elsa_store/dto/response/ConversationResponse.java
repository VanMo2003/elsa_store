package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ChatSide;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

/** Cuộc trò chuyện nhìn từ một phía: {@code unread} và {@code mySide} phụ thuộc người hỏi. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationResponse {
    private Long id;
    private Long storeId;
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private String storeImageUrl;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    /** Ảnh đại diện của khách (hộp thư cửa hàng). */
    private String customerAvatarUrl;
    private String lastMessage;
    private ChatSide lastSenderSide;
    private LocalDateTime lastMessageAt;
    private ChatSide mySide;
    private int unread;
    /** Trợ lý đã chuyển cuộc này cho nhân viên; chỉ có ý nghĩa ở phía cửa hàng. */
    private Boolean needsStaff;
    private String handoffReason;
    private LocalDateTime createdAt;
}
