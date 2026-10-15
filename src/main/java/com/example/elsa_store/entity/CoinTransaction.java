package com.example.elsa_store.entity;

import com.example.elsa_store.constant.CoinTransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một dòng sổ xu thưởng của khách. Số dư = tổng {@code amount} của mọi dòng; {@code balanceAfter} chụp số dư ngay sau
 * dòng này (ghi khi đang khoá tài khoản nên không lệch khi hai đơn đặt cùng lúc).
 */
@Entity
@Table(name = "coin_transactions", indexes = @Index(name = "idx_coin_user", columnList = "user_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinTransaction extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Dương = cộng xu, âm = trừ xu. */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 20, nullable = false)
    private CoinTransactionType type;

    /** Đơn liên quan; null nếu đơn đã bị xoá (mã đơn vẫn còn trong {@code note}). */
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    /** Sản phẩm được thưởng xu khi đánh giá (chỉ các dòng thưởng đánh giá). */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "note", length = 255)
    private String note;
}
