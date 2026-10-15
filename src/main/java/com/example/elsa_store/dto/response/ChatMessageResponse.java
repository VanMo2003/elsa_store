package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.constant.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private ChatSide senderSide;
    private Long senderUserId;
    private String senderName;
    private Role senderRole;
    private String content;
    /** Trợ lý AI viết thay cửa hàng. */
    private Boolean aiGenerated;
    private List<ChatProductResponse> products;
    private LocalDateTime createdAt;
}
