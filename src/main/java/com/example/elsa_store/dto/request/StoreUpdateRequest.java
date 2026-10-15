
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Chủ / quản lý sửa thông tin cửa hàng đã được duyệt. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreUpdateRequest {
    @NotBlank
    @Size(max = 200)
    String name;

    @Size(max = 5000)
    String description;

    @Size(max = 255)
    String imageUrl;

    @Email
    @Size(max = 100)
    String email;

    @Size(max = 20)
    String phone;

    @Size(max = 255)
    String address;
}
