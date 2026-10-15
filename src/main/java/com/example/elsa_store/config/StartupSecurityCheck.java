package com.example.elsa_store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Nhắc khi BE còn chạy với bí mật mặc định nằm trong repo (khoá ký JWT, tài khoản VNPay sandbox). Chỉ ghi log — máy phát
 * triển vẫn chạy bình thường, nhưng ai triển khai thật sẽ thấy ngay cần đặt biến môi trường nào.
 */
@Slf4j
@Component
public class StartupSecurityCheck {

    /** Khoá có sẵn trong application.yaml — ai đọc được repo cũng tự ký được token với khoá này. */
    static final String DEFAULT_JWT_KEY = "6lhXE9HRxzm7EyIVWdl0XPx9eoGdgXm8VNfpaBQ1dUDpZF6NcF8z4fDAO15JFAdF";

    private static final int MIN_HS512_KEY_BYTES = 64;

    private final String jwtKey;
    private final String vnpayUrl;

    public StartupSecurityCheck(
            @Value("${jwt.signer-key}") String jwtKey, @Value("${vnpay.url:}") String vnpayUrl) {
        // Khoá ngắn hơn 512 bit thì thư viện từ chối ký: mọi lần đăng nhập sẽ lỗi 500 khó hiểu — báo ngay lúc khởi động.
        if (jwtKey == null || jwtKey.getBytes().length < MIN_HS512_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SIGNER_KEY must be at least " + MIN_HS512_KEY_BYTES + " bytes long for HS512");
        }
        this.jwtKey = jwtKey;
        this.vnpayUrl = vnpayUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutDefaults() {
        if (DEFAULT_JWT_KEY.equals(jwtKey)) {
            log.warn("JWT_SIGNER_KEY is not set - tokens are signed with the development key from application.yaml. "
                    + "Set JWT_SIGNER_KEY (64+ random characters) before deploying.");
        }
        if (vnpayUrl.contains("sandbox")) {
            log.info("VNPay is in sandbox mode (test cards only)");
        }
    }
}
