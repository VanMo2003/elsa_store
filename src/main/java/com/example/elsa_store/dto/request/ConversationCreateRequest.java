package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationCreateRequest {
    /** Cửa hàng khách muốn nhắn tin. */
    @NotNull
    private Long storeId;
}
