package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiStoreSettingsRequest {
    @NotNull
    private Boolean autoReply;
}
