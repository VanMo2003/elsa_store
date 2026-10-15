package com.example.elsa_store.repository;

import com.example.elsa_store.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Repository
public interface InvalidatedRepository extends JpaRepository<InvalidatedToken, String> {

    /** Token đã hết hạn thì tự nó vô hiệu — không cần giữ trong danh sách thu hồi nữa. */
    @Modifying
    @Transactional
    @Query("delete from InvalidatedToken t where t.expiryTime < :now")
    int deleteExpired(@Param("now") Date now);
}
