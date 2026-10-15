package com.example.elsa_store.entity;

import com.example.elsa_store.constant.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một mốc trên dòng thời gian của đơn: đơn chuyển sang trạng thái nào, lúc nào ({@code created_at}), ai làm và ghi chú
 * (lý do huỷ, "Thanh toán VNPay thành công"…). Đơn đặt trước khi có bảng này không có mốc nào — app dựng lại từ ngày
 * đặt / ngày hoàn thành.
 */
@Entity
@Table(name = "order_status_history", indexes = @Index(name = "idx_order_history_order", columnList = "order_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private OrderStatus status;

    /** Người đổi trạng thái; trống khi hệ thống tự đổi (VNPay báo đã thanh toán). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "note", length = 255)
    private String note;
}
