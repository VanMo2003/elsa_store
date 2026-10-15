package com.example.elsa_store.dto.response;

import lombok.*;

/**
 * Kết quả gửi mã. Luôn giống nhau dù tài khoản có tồn tại hay không — không để lộ ai có tài khoản / email nào.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetResponse {
    /** Chờ bao nhiêu giây mới gửi lại được. */
    private int resendAfterSeconds;
    /** Mã có hiệu lực bao nhiêu phút. */
    private int validMinutes;
}
