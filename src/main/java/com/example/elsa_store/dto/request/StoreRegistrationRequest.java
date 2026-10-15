
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Đăng ký mở cửa hàng: tạo tài khoản STORE_OWNER và cửa hàng chờ ADMIN duyệt trong một lần. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreRegistrationRequest {
    @NotBlank
    @Size(min = 6, max = 50)
    String username;

    @NotBlank
    @Size(min = 6, max = 100)
    String password;

    @NotBlank
    @Size(max = 200)
    String storeName;

    @Size(max = 5000)
    String description;

    @NotBlank
    @Size(max = 100)
    String ownerName;

    @NotBlank
    @Email
    @Size(max = 100)
    String email;

    @NotBlank
    @Size(max = 20)
    String phone;

    @NotBlank
    @Size(max = 255)
    String address;
}
