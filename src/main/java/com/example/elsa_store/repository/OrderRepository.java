package com.example.elsa_store.repository;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.entity.Order;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUser_Id(Long userId);
    long countByStatus(OrderStatus status);

    @Query("""
        SELECT COALESCE(SUM(o.finalAmount),0)
        FROM Order o
        WHERE o.orderDate >= :start
        AND o.orderDate < :end
        AND o.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
    """)
    Double revenueBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
        select
            count(o.id),
            coalesce(sum(o.totalAmount), 0),
            coalesce(sum(o.discountAmount), 0),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where o.orderDate >= :from
          and o.orderDate < :to
          and o.status in :statuses
    """)
    Object[] revenueSummary(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("statuses") List<Integer> statuses);

    // Group theo DAY: period = yyyy-mm-dd
    @Query("""
        select
            function('date', o.orderDate),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where o.orderDate between :from and :to
          and o.status in :statuses
        group by function('date', o.orderDate)
        order by function('date', o.orderDate)
    """)
    List<Object[]> revenueByDay(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("statuses") List<Integer> statuses);

    // Group theo MONTH: period = yyyy-mm (tách year+month để group ổn định)
    @Query("""
        select
            function('year', o.orderDate),
            function('month', o.orderDate),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where o.orderDate between :from and :to
          and o.status in :statuses
        group by function('year', o.orderDate), function('month', o.orderDate)
        order by function('year', o.orderDate), function('month', o.orderDate)
    """)
    List<Object[]> revenueByMonth(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("statuses") List<Integer> statuses);

    // Group theo YEAR: period = yyyy
    @Query("""
        select
            function('year', o.orderDate),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where o.orderDate between :from and :to
          and o.status in :statuses
        group by function('year', o.orderDate)
        order by function('year', o.orderDate)
    """)
    List<Object[]> revenueByYear(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("statuses") List<Integer> statuses);
}
