package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Cart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long customerId);

    /**
     * Giỏ hàng bị bỏ quên của khách: còn món, lần thêm / sửa món gần nhất trước {@code cutoff}, và chưa nhắc kể từ lần
     * thay đổi đó (mỗi "lần bỏ quên" chỉ nhắc một lần).
     */
    @Query("""
        select c from Cart c
        where c.user.role = com.example.elsa_store.constant.Role.USER and c.user.enabled = true
          and exists (select i.id from CartItem i where i.cart = c)
          and (select max(i.updatedAt) from CartItem i where i.cart = c) < :cutoff
          and (c.remindedAt is null
               or c.remindedAt < (select max(i.updatedAt) from CartItem i where i.cart = c))
        order by c.id
    """)
    List<Cart> findAbandoned(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
