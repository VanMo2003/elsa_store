package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ProductViewDay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductViewDayRepository extends JpaRepository<ProductViewDay, Long> {

    /** Cộng một lượt xem cho sản phẩm trong ngày (một câu lệnh, an toàn khi nhiều người xem cùng lúc). */
    @Modifying
    @Query(value = """
        insert into product_view_days (product_id, store_id, view_date, views, created_at, updated_at)
        values (:productId, :storeId, :day, 1, now(), now())
        on duplicate key update views = views + 1, updated_at = now()
    """, nativeQuery = true)
    int increment(@Param("productId") Long productId, @Param("storeId") Long storeId, @Param("day") LocalDate day);

    /** Sản phẩm được xem nhiều nhất trong kỳ: mỗi dòng {productId, tổng lượt xem}. */
    @Query("""
        select v.productId, sum(v.views) from ProductViewDay v
        where (:storeId is null or v.storeId = :storeId) and v.viewDate >= :from and v.viewDate <= :to
        group by v.productId
        order by sum(v.views) desc
    """)
    List<Object[]> topViewed(@Param("storeId") Long storeId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);
}
