
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Chủ cửa hàng sửa hồ sơ (khi đang chờ duyệt hoặc bị từ chối) rồi gửi lại. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreResubmitRequest {
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
