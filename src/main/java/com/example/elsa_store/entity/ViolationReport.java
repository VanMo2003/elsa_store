package com.example.elsa_store.entity;

import com.example.elsa_store.constant.ViolationReason;
import com.example.elsa_store.constant.ViolationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Khách báo cáo một sản phẩm vi phạm; ADMIN xử lý hoặc bỏ qua kèm ghi chú. */
@Entity
@Table(
        name = "violation_reports",
        indexes = {
            @Index(name = "idx_violation_status", columnList = "status, id"),
            @Index(name = "idx_violation_product_user", columnList = "product_id, user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReport extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Cửa hàng của sản phẩm lúc báo cáo (để lọc / mở hồ sơ cửa hàng). */
    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    /** Khách báo cáo. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason", length = 30, nullable = false)
    private ViolationReason reason;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ViolationStatus status = ViolationStatus.PENDING;

    /** Ghi chú của ADMIN khi xử lý (gửi cho người báo cáo). */
    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
