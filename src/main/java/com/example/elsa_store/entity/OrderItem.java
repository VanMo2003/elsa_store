
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "quantity")
    private Integer quantity;

    /** Đơn giá khách trả (đã trừ khuyến mãi theo sản phẩm). */
    @Column(name = "unit_price")
    private Double unitPrice;

    /** Tổng tiền được giảm của dòng này so với giá gốc. */
    @Column(name = "discount")
    private Double discount;

    @Column(name = "line_total")
    private Double lineTotal;

    /** Đơn giá gốc trước khuyến mãi. */
    @Column(name = "original_unit_price")
    private Double originalUnitPrice;

    /** Đã trừ kho cho dòng này lúc đặt — chỉ dòng đã trừ mới được hoàn kho khi huỷ (đơn cũ trước nâng cấp thì không). */
    @Column(name = "stock_reserved")
    private Boolean stockReserved;

    /** Giá vốn một đơn vị lúc bán (chụp từ biến thể) — để tính lợi nhuận đúng kỳ dù giá vốn đổi về sau. */
    @Column(name = "cost_price")
    private Double costPrice;
}
