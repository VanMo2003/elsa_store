package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Khôi phục mật khẩu: {@code identifier} là tên đăng nhập hoặc email. Bước gửi mã chỉ cần {@code identifier}; bước kiểm
 * mã cần thêm {@code code}; bước đặt lại cần cả {@code newPassword}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetRequest {

    @NotBlank
    @Size(max = 100)
    private String identifier;

    @Pattern(regexp = "\\d{6}")
    private String code;

    @Size(min = 6, max = 100)
    private String newPassword;
}
