package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Map;

/** Bật / tắt nhóm thông báo: {@code {"PRICE_DROP": false}}; nhóm không gửi lên giữ nguyên. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsRequest {

    @NotEmpty
    private Map<String, Boolean> groups;
}
