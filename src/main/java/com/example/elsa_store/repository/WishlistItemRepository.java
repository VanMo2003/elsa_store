package com.example.elsa_store.repository;

import com.example.elsa_store.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUser_IdOrderByIdDesc(Long userId);

    Optional<WishlistItem> findByUser_IdAndProduct_Id(Long userId, Long productId);

    @Query("select w.product.id from WishlistItem w where w.user.id = :userId order by w.id desc")
    List<Long> productIds(@Param("userId") Long userId);

    long countByUser_Id(Long userId);

    /** Khách đã thả tim một sản phẩm (báo "có hàng trở lại"). */
    List<WishlistItem> findAllByProduct_Id(Long productId);

    @Modifying
    @Query("delete from WishlistItem w where w.product.id = :productId")
    int deleteAllByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
