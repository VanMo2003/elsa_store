package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_customer", columnNames = {"customer_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    // optional: lưu snapshot tổng tiền (có thể không cần, tính động)
    @Column(name = "total_amount")
    private Double totalAmount;

    /** Lần gần nhất nhắc khách về giỏ hàng bị bỏ quên (null = chưa nhắc). */
    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;
}
