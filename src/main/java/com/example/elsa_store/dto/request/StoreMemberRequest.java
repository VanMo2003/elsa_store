
package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Tạo tài khoản quản lý (chỉ chủ cửa hàng) hoặc nhân viên (chủ và quản lý) cho cửa hàng của người gọi. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreMemberRequest {
    @NotBlank
    @Size(min = 6, max = 50)
    String username;

    @NotBlank
    @Size(min = 6, max = 100)
    String password;

    @NotBlank
    @Size(max = 100)
    String fullName;

    @Email
    @Size(max = 100)
    String email;

    @Size(max = 20)
    String phone;

    /** STORE_MANAGER hoặc STAFF. */
    @NotNull
    Role role;
}
