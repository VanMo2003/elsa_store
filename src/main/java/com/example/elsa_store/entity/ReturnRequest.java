package com.example.elsa_store.entity;

import com.example.elsa_store.constant.ReturnStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Yêu cầu trả hàng của khách cho một đơn đã hoàn thành. Tiền hoàn ghi nhận vào ngày {@code refundedAt} (báo cáo trừ khỏi
 * doanh thu kỳ đó); hàng trả về kho lúc hoàn tiền.
 */
@Entity
@Table(
        name = "return_requests",
        indexes = {
                @Index(name = "idx_return_store", columnList = "store_id, id"),
                @Index(name = "idx_return_order", columnList = "order_id"),
                @Index(name = "idx_return_user", columnList = "user_id, id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest extends BaseEntity {

    /** Mã dễ đọc: TH-yyMMdd-XXXX. */
    @Column(name = "code", length = 30, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** Khách gửi yêu cầu (người đặt đơn). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private ReturnStatus status;

    @Column(name = "reason", length = 255, nullable = false)
    private String reason;

    /** Mô tả thêm của khách. */
    @Column(name = "note", length = 1000)
    private String note;

    /** Lời nhắn của cửa hàng: lý do từ chối, hướng dẫn gửi hàng, ghi chú hoàn tiền. */
    @Column(name = "store_note", length = 500)
    private String storeNote;

    /** Số tiền gợi ý hoàn: giá khách đã trả cho các món trả (đã chia phần giảm giá cả đơn, không gồm phí giao). */
    @Column(name = "suggested_refund")
    private Double suggestedRefund;

    /** Số tiền cửa hàng thực hoàn. */
    @Column(name = "refund_amount")
    private Double refundAmount;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Builder.Default
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnItem> items = new ArrayList<>();
}
