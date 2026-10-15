package com.example.elsa_store.repository;

import com.example.elsa_store.entity.PromotionProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface PromotionProductRepository extends JpaRepository<PromotionProduct, Long> {

    /** Khuyến mãi tự động (không cần mã) đang chạy hôm nay cho các sản phẩm này — giảm thẳng vào giá bán. */
    @Query("""
        select pp from PromotionProduct pp join fetch pp.promotion p
        where pp.product.id in :productIds
          and p.status = 1
          and (p.couponCode is null or p.couponCode = '')
          and (p.startDate is null or p.startDate <= :today)
          and (p.endDate is null or p.endDate >= :today)
    """)
    List<PromotionProduct> findActiveAutomatic(
            @Param("productIds") Collection<Long> productIds, @Param("today") LocalDate today);

    /** Id sản phẩm đang có khuyến mãi tự động chạy hôm nay (dải "Đang giảm giá" ở trang chủ). */
    @Query("""
        select distinct pp.product.id from PromotionProduct pp join pp.promotion p
        where p.status = 1
          and (p.couponCode is null or p.couponCode = '')
          and (p.startDate is null or p.startDate <= :today)
          and (p.endDate is null or p.endDate >= :today)
    """)
    List<Long> productIdsOnSale(@Param("today") LocalDate today);

    List<PromotionProduct> findAllByPromotion_Id(Long promotionId);

    boolean existsByPromotion_IdAndProduct_Id(Long promotionId, Long productId);

    void deleteAllByPromotion_Id(Long promotionId);
}
