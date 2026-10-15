package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Bật / tắt tạm nghỉ bán; {@code note} là lời nhắn cho khách (bỏ qua khi tắt). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorePauseRequest {
    @NotNull
    Boolean paused;

    @Size(max = 255)
    String note;
}
