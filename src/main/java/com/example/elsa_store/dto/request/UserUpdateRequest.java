
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Cập nhật tài khoản. Chỉ chủ tài khoản đổi được mật khẩu và hồ sơ của mình; ADMIN chỉ khoá / mở tài khoản.
 * Vai trò không bao giờ đổi được.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    @Size(max = 100)
    String fullName;

    @Email
    String email;

    @Size(max = 20)
    String phone;

    @Size(min = 6, max = 100)
    String password;

    /** Bắt buộc khi đổi {@link #password}. Nên dùng {@code PUT /users/me/password} (trả luôn token mới). */
    String currentPassword;

    /** Khoá / mở tài khoản — chỉ ADMIN. */
    Boolean enabled;
}
