package com.example.elsa_store.repository;

import com.example.elsa_store.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** Mã mới nhất của tài khoản (kể cả đã dùng / hết hạn — để tính thời gian chờ gửi lại). */
    Optional<PasswordResetCode> findFirstByUser_IdOrderByIdDesc(Long userId);

    long countByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Có mã mới thì mọi mã cũ chưa dùng hết hiệu lực. */
    @Modifying
    @Query("update PasswordResetCode c set c.usedAt = :now where c.user.id = :userId and c.usedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from PasswordResetCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
