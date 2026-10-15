package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

/** Một món trong yêu cầu trả hàng: dòng đơn gốc, số lượng trả và đơn giá hoàn gợi ý. */
@Entity
@Table(name = "return_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ReturnRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Giá khách đã trả cho một món (sau khuyến mãi và phần giảm giá cả đơn chia đều theo giá trị). */
    @Column(name = "unit_refund")
    private Double unitRefund;
}
