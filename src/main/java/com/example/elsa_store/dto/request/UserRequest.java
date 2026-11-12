
package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.Role;
import jakarta.validation.constraints.Email;
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
public class UserRequest {
    @NotBlank
    @Size(min = 6, max = 50)
    String username;

    /** Bắt buộc khi đăng ký; khi cập nhật hồ sơ có thể bỏ trống (mật khẩu đổi ở /users/{id}/password). */
    @Size(min = 6, max = 100)
    String password;

    @Email
    String email;
    String phone;
    String fullName;

    /** Đăng ký công khai luôn là USER; các vai trò khác có luồng tạo riêng. */
    @Builder.Default
    Role role = Role.USER;

    Boolean enabled;

    /** Mã giới thiệu của bạn bè (tuỳ chọn, chỉ khi khách tự đăng ký). */
    @Size(max = 20)
    String referralCode;
}
