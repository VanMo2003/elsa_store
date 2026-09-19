
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceTokenRequest {
    @NotBlank
    @Size(max = 512)
    String token;

    /** android, ios hoặc web. */
    @Size(max = 20)
    String platform;
}
