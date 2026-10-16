package com.example.elsa_store.repository;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Sản phẩm bán chạy: id biến thể, tên sản phẩm, ảnh, số lượng, doanh thu, giá vốn (các dòng đã có giá vốn), số lượng
     * bán chưa có giá vốn. {@code storeId} null = toàn hệ thống.
     */
    @Query("""
        select
            i.productVariant.id,
            i.productVariant.product.name,
            i.productVariant.imageUrl,
            coalesce(sum(i.quantity), 0),
            coalesce(sum(i.lineTotal), 0),
            coalesce(sum(case when i.costPrice is not null then i.costPrice * i.quantity else 0.0 end), 0.0),
            coalesce(sum(case when i.costPrice is null then i.quantity else 0 end), 0)
        from OrderItem i
        join i.order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by i.productVariant.id, i.productVariant.product.name, i.productVariant.imageUrl
        order by coalesce(sum(i.lineTotal), 0) desc
    """)
    List<Object[]> topProductRevenue(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("statuses") Collection<OrderStatus> statuses,
                                     @Param("storeId") Long storeId,
                                     Pageable pageable);

    /** Doanh thu theo danh mục: id, tên danh mục, số lượng bán, doanh thu. */
    @Query("""
        select
            c.id,
            coalesce(c.name, 'Chưa phân loại'),
            coalesce(sum(i.quantity), 0),
            coalesce(sum(i.lineTotal), 0)
        from OrderItem i
        join i.order o
        join i.productVariant.product p
        left join p.category c
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
        group by c.id, coalesce(c.name, 'Chưa phân loại')
        order by coalesce(sum(i.lineTotal), 0) desc
    """)
    List<Object[]> revenueByCategory(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("statuses") Collection<OrderStatus> statuses,
                                     @Param("storeId") Long storeId);

    /** Giá vốn hàng đã bán trong kỳ: tổng giá vốn các dòng đã có giá vốn, số lượng bán chưa có giá vốn. */
    @Query("""
        select
            coalesce(sum(case when i.costPrice is not null then i.costPrice * i.quantity else 0.0 end), 0.0),
            coalesce(sum(case when i.costPrice is null then i.quantity else 0 end), 0)
        from OrderItem i
        join i.order o
        where coalesce(o.completedAt, o.orderDate) >= :from
          and coalesce(o.completedAt, o.orderDate) < :to
          and o.status in :statuses
          and (:storeId is null or o.store.id = :storeId)
    """)
    List<Object[]> costSummary(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("statuses") Collection<OrderStatus> statuses,
                               @Param("storeId") Long storeId);

    /** Dòng hàng mới nhất của sản phẩm trong các đơn đã hoàn thành của khách — điều kiện để viết đánh giá. */
    /** Mỗi dòng {productId, số đơn, số món} của đơn (trừ đơn huỷ) đặt trong kỳ có các sản phẩm {@code productIds}. */
    @Query("""
        select i.productVariant.product.id, count(distinct o.id), sum(i.quantity)
        from OrderItem i join i.order o
        where i.productVariant.product.id in :productIds
          and o.orderDate >= :from and o.orderDate < :to
          and o.status <> com.example.elsa_store.constant.OrderStatus.DA_HUY
        group by i.productVariant.product.id
    """)
    List<Object[]> ordersForProducts(@Param("productIds") Collection<Long> productIds,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * "Khách mua sản phẩm này cũng mua": sản phẩm khác nằm chung đơn (không tính đơn huỷ) với {@code productId}, nhiều đơn
     * chung nhất trước, hoà thì đơn mới hơn trước. Mỗi dòng {productId, số đơn chung}.
     */
    @Query("""
        select other.productVariant.product.id, count(distinct other.order.id)
        from OrderItem mine, OrderItem other
        where mine.productVariant.product.id = :productId
          and other.order.id = mine.order.id
          and other.productVariant.product.id <> :productId
          and mine.order.status <> com.example.elsa_store.constant.OrderStatus.DA_HUY
        group by other.productVariant.product.id
        order by count(distinct other.order.id) desc, max(other.order.id) desc
    """)
    List<Object[]> boughtTogether(@Param("productId") Long productId, Pageable pageable);

    @Query("""
        select i from OrderItem i
        join i.order o
        where o.user.id = :userId
          and i.productVariant.product.id = :productId
          and o.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
        order by o.id desc
    """)
    List<OrderItem> completedPurchases(@Param("userId") Long userId,
                                       @Param("productId") Long productId,
                                       Pageable pageable);

    /** Id sản phẩm bán được nhiều món nhất trong các đơn hoàn thành từ {@code since} (đơn cũ chưa có ngày hoàn thành thì
     *  lấy ngày đặt). */
    @Query("""
        select i.productVariant.product.id from OrderItem i
        where i.order.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
          and coalesce(i.order.completedAt, i.order.orderDate) >= :since
        group by i.productVariant.product.id
        order by sum(i.quantity) desc, i.productVariant.product.id desc
    """)
    List<Long> bestSellingProductIds(@Param("since") LocalDateTime since, Pageable pageable);

    /** Số món đã bán (đơn hoàn thành) của từng sản phẩm: các dòng {productId, tổng số món}. */
    @Query("""
        select i.productVariant.product.id, sum(i.quantity) from OrderItem i
        where i.order.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
          and i.productVariant.product.id in :productIds
        group by i.productVariant.product.id
    """)
    List<Object[]> soldByProduct(@Param("productIds") Collection<Long> productIds);

    /** Tổng số món đã giao xong của một cửa hàng ("đã bán"). */
    @Query("""
        select coalesce(sum(i.quantity), 0) from OrderItem i
        where i.order.store.id = :storeId
          and i.order.status = com.example.elsa_store.constant.OrderStatus.HOAN_THANH
    """)
    long soldQuantity(@Param("storeId") Long storeId);
}
