package com.example.elsa_store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tách khỏi {@link SecurityConfig}: dịch vụ đăng nhập (mà bộ giải mã JWT phụ thuộc) cần bộ mã hoá mật khẩu, nên để
 * chung một chỗ thì Spring gặp vòng phụ thuộc lúc khởi động.
 */
@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
