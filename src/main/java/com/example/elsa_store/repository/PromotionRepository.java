package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findAllByStore_Id(Long storeId);

    /**
     * Khuyến mãi tự động áp cho cả đơn (không mã, không gắn sản phẩm cụ thể) đang chạy hôm nay — ví dụ "giảm 10% cho đơn
     * từ 500.000đ".
     */
    @Query("""
        select p from Promotion p
        where p.store.id = :storeId
          and p.status = 1
          and (p.couponCode is null or p.couponCode = '')
          and (p.startDate is null or p.startDate <= :today)
          and (p.endDate is null or p.endDate >= :today)
          and not exists (select pp.id from PromotionProduct pp where pp.promotion = p)
    """)
    List<Promotion> findActiveStoreWide(@Param("storeId") Long storeId, @Param("today") LocalDate today);

    /** Mã giảm giá của một cửa hàng (không phân biệt hoa thường). */
    @Query("select p from Promotion p where p.store.id = :storeId and lower(p.couponCode) = lower(:code)")
    List<Promotion> findByStoreAndCoupon(@Param("storeId") Long storeId, @Param("code") String code);

    /** Mã giảm giá đang chạy hôm nay của một cửa hàng — app gợi ý cho khách ở bước thanh toán. */
    @Query("""
        select p from Promotion p
        where p.store.id = :storeId
          and p.status = 1
          and p.couponCode is not null and p.couponCode <> ''
          and (p.startDate is null or p.startDate <= :today)
          and (p.endDate is null or p.endDate >= :today)
        order by p.id desc
    """)
    List<Promotion> findActiveCoupons(@Param("storeId") Long storeId, @Param("today") LocalDate today);
}
