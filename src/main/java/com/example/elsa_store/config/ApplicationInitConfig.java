package com.example.elsa_store.config;


import com.example.elsa_store.entity.User;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {

    PasswordEncoder passwordEncoder;

    @NonFinal
    static String ADMIN_USER_NAME = "admin123";

    @NonFinal
    static String ADMIN_PASSWORD = "admin123";

    /**
     * Be run every time the application start
     * @return
     */
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {
            // Tài khoản admin mặc định phải luôn dùng được (bản cũ tạo nó với enabled = false vì lỗi builder).
            userRepository.findByUsername(ADMIN_USER_NAME)
                    .filter(admin -> !admin.isEnabled())
                    .ifPresent(admin -> {
                        admin.setEnabled(true);
                        userRepository.save(admin);
                    });
            if (userRepository.findByUsername(ADMIN_USER_NAME).isEmpty()) {

                User user = User.builder()
                        .username(ADMIN_USER_NAME)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(Role.ADMIN)
                        .build();

                userRepository.save(user);
                log.warn(
                        "Created the default admin account '{}' with the default password - change it after the first login",
                        ADMIN_USER_NAME);
            }
        };
    }
}
