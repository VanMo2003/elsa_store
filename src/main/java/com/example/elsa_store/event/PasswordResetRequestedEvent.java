package com.example.elsa_store.event;

/** Có yêu cầu khôi phục mật khẩu: gửi mã cho chủ tài khoản qua email (sau khi giao dịch commit). */
public record PasswordResetRequestedEvent(String email, String name, String username, String code, int validMinutes) {

    /** Không để mã lọt vào log khi bản ghi sự kiện bị in ra. */
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[username=" + username + ", email=" + email + "]";
    }
}
