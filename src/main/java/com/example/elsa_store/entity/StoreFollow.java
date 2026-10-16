package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

/** Khách theo dõi một cửa hàng (mỗi cặp một dòng): nhận thông báo khi cửa hàng mở bán sản phẩm mới. */
@Entity
@Table(
        name = "store_follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_follow_user_store", columnNames = {"user_id", "store_id"}),
        indexes = @Index(name = "idx_follow_store", columnList = "store_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreFollow extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;
}
