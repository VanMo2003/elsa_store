package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.PasswordResetResponse;

/**
 * Quên mật khẩu bằng mã 6 số gửi qua email. Ba bước: gửi mã → kiểm mã (không tiêu mã, để app báo sai ngay) → đặt mật
 * khẩu mới (tiêu mã, đăng xuất mọi thiết bị). Không bao giờ cho biết tài khoản / email có tồn tại hay không.
 */
public interface PasswordResetService {

    /** {@code identifier}: tên đăng nhập hoặc email. */
    PasswordResetResponse requestCode(String identifier, String clientIp);

    void verifyCode(String identifier, String code);

    void resetPassword(String identifier, String code, String newPassword);
}
