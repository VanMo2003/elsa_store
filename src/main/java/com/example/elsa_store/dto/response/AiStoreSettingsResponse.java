package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiStoreSettingsResponse {
    private Long storeId;
    private boolean autoReply;
    /** Máy chủ đã có khoá AI hay chưa; chưa có thì mọi tính năng AI tắt. */
    private boolean aiEnabled;
    private long autoReplyDelaySeconds;
}
