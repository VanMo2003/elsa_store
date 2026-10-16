package com.example.elsa_store.repository;

import com.example.elsa_store.constant.ReturnStatus;
import com.example.elsa_store.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    boolean existsByCode(String code);

    /** Yêu cầu của một khách, mới nhất trước; {@code orderId} null = mọi đơn. */
    /** Hạng thành viên: tiền đã hoàn cho các đơn hoàn thành từ {@code since} của khách. */
    @Query("""
        select coalesce(sum(r.refundAmount), 0) from ReturnRequest r
        where r.order.user.id = :userId
          and r.status = com.example.elsa_store.constant.ReturnStatus.DA_HOAN_TIEN
          and coalesce(r.order.completedAt, r.order.orderDate) >= :since
          and (:excludeId is null or r.order.id <> :excludeId)
    """)
    double memberRefunds(@Param("userId") Long userId,
                         @Param("since") LocalDateTime since,
                         @Param("excludeId") Long excludeId);

    @Query("""
        select r from ReturnRequest r
        where r.user.id = :userId and (:orderId is null or r.order.id = :orderId)
        order by r.id desc
    """)
    Page<ReturnRequest> findMine(@Param("userId") Long userId, @Param("orderId") Long orderId, Pageable pageable);

    /** Yêu cầu gửi tới một cửa hàng ({@code storeId} null = mọi cửa hàng, cho ADMIN), lọc trạng thái. */
    @Query("""
        select r from ReturnRequest r
        where (:storeId is null or r.store.id = :storeId)
          and (:status is null or r.status = :status)
        order by r.id desc
    """)
    Page<ReturnRequest> findForStore(@Param("storeId") Long storeId,
                                     @Param("status") ReturnStatus status,
                                     Pageable pageable);

    boolean existsByOrder_IdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);

    /** Số lượng đã / đang trả của từng dòng đơn (các yêu cầu còn hiệu lực): {orderItemId, sum}. */
    @Query("""
        select i.orderItem.id, sum(i.quantity) from ReturnItem i
        where i.request.order.id = :orderId and i.request.status in :statuses
        group by i.orderItem.id
    """)
    List<Object[]> returnedQuantities(@Param("orderId") Long orderId,
                                      @Param("statuses") Collection<ReturnStatus> statuses);

    /** Tổng tiền hoàn và số yêu cầu đã hoàn trong {@code [from, to)}: một dòng {sum, count}. */
    @Query("""
        select coalesce(sum(r.refundAmount), 0), count(r.id) from ReturnRequest r
        where r.status = com.example.elsa_store.constant.ReturnStatus.DA_HOAN_TIEN
          and r.refundedAt >= :from and r.refundedAt < :to
          and (:storeId is null or r.store.id = :storeId)
    """)
    List<Object[]> refundSummary(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("storeId") Long storeId);

    /** Số yêu cầu chờ duyệt của cửa hàng (tổng quan, Việc cần làm). */
    long countByStore_IdAndStatus(Long storeId, ReturnStatus status);
}
