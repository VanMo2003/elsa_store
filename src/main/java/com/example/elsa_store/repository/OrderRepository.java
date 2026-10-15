package com.example.elsa_store.repository;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mọi truy vấn báo cáo nhận {@code storeId}: có giá trị thì chỉ tính cửa hàng đó, null thì tính toàn hệ thống
 * (chỉ ADMIN được gọi với null). Khoảng thời gian là nửa mở {@code [from, to)}.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Khoá dòng đơn khi hệ thống tự đổi trạng thái, để không chồng lên một thao tác khác cùng lúc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /**
     * Đơn VNPay chưa trả, chưa xác nhận, đặt trước {@code cutoff} và không có lần mở link thanh toán nào từ {@code cutoff}
     * — link VNPay hết hạn sau 15 phút nên khách không còn trả được nữa.
     */
    @Query("""
        select o.id from Order o
        where o.status = com.example.elsa_store.constant.OrderStatus.CHUA_XAC_NHAN
          and o.paymentMethod = 'VNPAY'
          and (o.paymentStatus is null or o.paymentStatus <> com.example.elsa_store.constant.PaymentStatus.DA_THANH_TOAN)
          and o.orderDate < :cutoff
          and not exists (select p.id from Payment p where p.order = o and p.createdAt >= :cutoff)
        order by o.id
    """)
    List<Long> findUnpaidOnlineOrderIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    /** Đơn đang giao từ trước {@code cutoff} (theo mốc DANG_GIAO gần nhất trong dòng thời gian). */
    @Query("""
        select o.id from Order o
        where o.status = com.example.elsa_store.constant.OrderStatus.DANG_GIAO
          and (select max(h.createdAt) from OrderStatusHistory h
               where h.order = o and h.status = com.example.elsa_store.constant.OrderStatus.DANG_GIAO) < :cutoff
        order by o.id
    """)
    List<Long> findLongShippingOrderIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    List<Order> findAllByUser_Id(Long userId);

    List<Order> findAllByUser_IdOrderByOrderDateDesc(Long userId);

    boolean existsByCode(String code);

    /** Bỏ liên kết tới địa chỉ sắp xoá — đơn đã chụp lại người nhận / địa chỉ lúc đặt nên không mất thông tin. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Order o set o.address = null where o.address.id = :addressId")
    int detachAddress(@Param("addressId") Long addressId);

    boolean existsByHandledBy_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    boolean existsByCustomer_Id(Long customerId);

    boolean existsByCustomer_IdAndStore_Id(Long customerId, Long storeId);

    List<Order> findAllByStore_IdOrderByOrderDateDesc(Long storeId);

    List<Order> findAllByStore_IdAndUser_Id(Long storeId, Long userId);

    long countByStore_IdAndStatus(Long storeId, OrderStatus status);

    long countByStatus(OrderStatus status);

    /** Doanh thu đơn hoàn thành, ghi nhận theo ngày hoàn thành (đơn cũ chưa có thì theo ngày đặt). */
    @Query("""
        SELECT COALESCE(SUM(o.finalAmount),0)
        FROM Order o
        WHERE coalesce(o.completedAt, o.orderDate) >= :start
          AND coalesce(o.completedAt, o.orderDate) < :end
          AND o.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
          AND (:storeId IS NULL OR o.store.id = :storeId)
    """)
    Double revenueBetween(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("storeId") Long storeId);

    @Query("""
        select
            count(o.id),
            coalesce(sum(o.totalAmount), 0),
            coalesce(sum(o.discountAmount), 0),
            coalesce(sum(o.finalAmount), 0),
            coalesce(sum(o.shippingFee), 0)
        from Order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
    """)
    List<Object[]> revenueSummary(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("statuses") Collection<OrderStatus> statuses,
                                  @Param("storeId") Long storeId);

    // Group theo DAY: period = yyyy-mm-dd
    @Query("""
        select
            function('date', coalesce(o.completedAt, o.orderDate)),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by function('date', coalesce(o.completedAt, o.orderDate))
        order by function('date', coalesce(o.completedAt, o.orderDate))
    """)
    List<Object[]> revenueByDay(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("statuses") Collection<OrderStatus> statuses,
                                @Param("storeId") Long storeId);

    // Group theo WEEK (tuần ISO, thứ Hai đầu tuần): yearweek = yyyyww
    @Query("""
        select
            function('yearweek', coalesce(o.completedAt, o.orderDate), 3),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by function('yearweek', coalesce(o.completedAt, o.orderDate), 3)
        order by function('yearweek', coalesce(o.completedAt, o.orderDate), 3)
    """)
    List<Object[]> revenueByWeek(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("statuses") Collection<OrderStatus> statuses,
                                 @Param("storeId") Long storeId);

    // Group theo MONTH: tách year + month để group ổn định
    @Query("""
        select
            function('year', coalesce(o.completedAt, o.orderDate)),
            function('month', coalesce(o.completedAt, o.orderDate)),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by function('year', coalesce(o.completedAt, o.orderDate)), function('month', coalesce(o.completedAt, o.orderDate))
        order by function('year', coalesce(o.completedAt, o.orderDate)), function('month', coalesce(o.completedAt, o.orderDate))
    """)
    List<Object[]> revenueByMonth(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("statuses") Collection<OrderStatus> statuses,
                                  @Param("storeId") Long storeId);

    // Group theo YEAR
    @Query("""
        select
            function('year', coalesce(o.completedAt, o.orderDate)),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by function('year', coalesce(o.completedAt, o.orderDate))
        order by function('year', coalesce(o.completedAt, o.orderDate))
    """)
    List<Object[]> revenueByYear(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("statuses") Collection<OrderStatus> statuses,
                                 @Param("storeId") Long storeId);

    /** Khách mua nhiều nhất: id khách, tên, số điện thoại, số đơn, tổng tiền. */
    @Query("""
        select
            c.id,
            coalesce(c.fullName, u.fullName, u.username),
            coalesce(c.phone, u.phone),
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        join o.customer c
        left join c.user u
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by c.id, coalesce(c.fullName, u.fullName, u.username), coalesce(c.phone, u.phone)
        order by coalesce(sum(o.finalAmount), 0) desc
    """)
    List<Object[]> topCustomers(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("statuses") Collection<OrderStatus> statuses,
                                @Param("storeId") Long storeId,
                                org.springframework.data.domain.Pageable pageable);

    /** Hiệu suất nhân viên lập đơn tại quầy: id, tên, vai trò, số đơn, tổng tiền. */
    @Query("""
        select
            h.id,
            coalesce(h.fullName, h.username),
            h.role,
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        join o.handledBy h
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by h.id, coalesce(h.fullName, h.username), h.role
        order by coalesce(sum(o.finalAmount), 0) desc
    """)
    List<Object[]> staffPerformance(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("statuses") Collection<OrderStatus> statuses,
                                    @Param("storeId") Long storeId);

    @Query("""
        select count(o.id)
        from Order o
        where o.orderDate >= :from
          and o.orderDate < :to
          and (:status is null or o.status = :status)
          and (:storeId is null or o.store.id = :storeId)
    """)
    long countInRange(@Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to,
                      @Param("status") OrderStatus status,
                      @Param("storeId") Long storeId);

    /**
     * Doanh thu theo cách trả tiền và người lập đơn: phương thức (COD / VNPAY), id nhân sự lập đơn tại quầy (null = khách
     * tự đặt online), số đơn, tổng tiền. Gộp thành "online / tại quầy" ở tầng dịch vụ.
     */
    @Query("""
        select
            o.paymentMethod,
            h.id,
            count(o.id),
            coalesce(sum(o.finalAmount), 0)
        from Order o
        left join o.handledBy h
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by o.paymentMethod, h.id
    """)
    List<Object[]> revenueByChannel(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("statuses") Collection<OrderStatus> statuses,
                                    @Param("storeId") Long storeId);
}
