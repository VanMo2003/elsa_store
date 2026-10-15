package com.example.elsa_store.repository;

import com.example.elsa_store.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** Dòng thời gian của một đơn, cũ nhất trước. */
    @EntityGraph(attributePaths = "changedBy")
    List<OrderStatusHistory> findByOrder_IdOrderByIdAsc(Long orderId);

    @Modifying
    @Query("delete from OrderStatusHistory h where h.order.id in :orderIds")
    void deleteByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
