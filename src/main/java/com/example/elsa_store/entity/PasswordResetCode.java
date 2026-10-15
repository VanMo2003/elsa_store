package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mã khôi phục mật khẩu gửi qua email. Chỉ lưu bản băm của mã; mỗi mã sống 10 phút, sai quá 5 lần thì hỏng, dùng xong
 * (hoặc khi có mã mới) thì bị đánh dấu đã dùng.
 */
@Entity
@Table(name = "password_reset_codes", indexes = @Index(name = "idx_reset_code_user", columnList = "user_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetCode extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", length = 100, nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
