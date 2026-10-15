package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatUnreadResponse {
    private long unread;
}
