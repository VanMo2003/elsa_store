package com.example.elsa_store.entity;

import com.example.elsa_store.constant.StockMovementType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Một dòng thẻ kho: mọi lần số lượng tồn của biến thể thay đổi (bán, hoàn, nhập, kiểm kê) đều để lại một dòng, nên chủ
 * cửa hàng tra được vì sao tồn kho ra con số hiện tại.
 */
@Entity
@Table(
        name = "stock_movements",
        indexes = {
                @Index(name = "idx_stock_movement_variant", columnList = "product_variant_id, created_at"),
                @Index(name = "idx_stock_movement_store", columnList = "store_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private StockMovementType type;

    /** Số lượng thay đổi, có dấu: nhập / hoàn là dương, bán là âm. */
    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    /** Tồn kho ngay sau thay đổi; trống khi ngừng theo dõi kho. */
    @Column(name = "balance_after")
    private Integer balanceAfter;

    /** Đơn giá vốn của lần nhập / giá vốn lúc bán. */
    @Column(name = "unit_cost")
    private Double unitCost;

    /** Chứng từ gốc: ORDER hoặc PURCHASE_ORDER. */
    @Column(name = "ref_type", length = 20)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /** Mã chứng từ (mã đơn / mã phiếu nhập) — vẫn đọc được khi chứng từ đã bị xoá. */
    @Column(name = "ref_code", length = 50)
    private String refCode;

    @Column(name = "note", length = 255)
    private String note;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;
}
