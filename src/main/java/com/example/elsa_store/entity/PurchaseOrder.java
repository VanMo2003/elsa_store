package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phiếu nhập hàng. Vòng đời: nháp (sửa / huỷ / xoá được) → đã nhập kho (cộng kho, tính lại giá vốn; khoá lại không sửa
 * nữa) hoặc đã huỷ.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseEntity {

    public static final int DRAFT = 0;
    public static final int RECEIVED = 1;
    public static final int CANCELLED = 2;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    /** Mã phiếu dạng PN-yyMMdd-XXXX (phiếu cũ trước nâng cấp để trống). */
    @Column(name = "code", length = 30, unique = true)
    private String code;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "status")
    private Integer status; // 0: nháp, 1: đã nhập kho, 2: đã huỷ

    /** Lúc bấm nhập kho. */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items;
}
