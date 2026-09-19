package com.example.elsa_store.repository;

import com.example.elsa_store.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    Optional<DeviceToken> findByTokenAndUser_Id(String token, Long userId);

    @Query("select d.token from DeviceToken d where d.user.id in :userIds")
    List<String> findTokensByUserIds(@Param("userIds") Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    int deleteAllByTokens(@Param("tokens") Collection<String> tokens);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
