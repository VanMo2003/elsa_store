package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Lượt xem trang sản phẩm theo ngày (khách và người chưa đăng nhập; người của cửa hàng / ADMIN không tính). Lưu id thô,
 * không khoá ngoại, để xoá sản phẩm không vướng số liệu thống kê.
 */
@Entity
@Table(
        name = "product_view_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_view_product_day", columnNames = {"product_id", "view_date"}),
        indexes = @Index(name = "idx_view_store_day", columnList = "store_id, view_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductViewDay extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "views", nullable = false)
    private Long views;
}
