package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    boolean authenticated;
    String accessToken;
    String refreshToken;
    Role role;
    UserResponse user;

    /** Cửa hàng của chủ / quản lý / nhân viên (kèm trạng thái duyệt) — app dùng để chọn màn hình sau đăng nhập. */
    StoreResponse store;
}
