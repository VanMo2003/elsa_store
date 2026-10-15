package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Đổi mật khẩu của chính mình — phải nhập đúng mật khẩu hiện tại (token bị lộ thì cũng không đổi được mật khẩu). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank
    String currentPassword;

    @NotBlank
    @Size(min = 6, max = 100)
    String newPassword;
}
