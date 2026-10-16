package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Khách xoá tài khoản của mình: nhập lại mật khẩu để xác nhận. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequest {

    @NotBlank
    private String password;
}
