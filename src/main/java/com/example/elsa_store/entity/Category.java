
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** Cửa hàng sở hữu danh mục (mỗi cửa hàng có bộ danh mục riêng); trống = danh mục chung cũ trước khi tách. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** Danh mục con (tầng 2). Không cascade xoá: service chặn xoá danh mục còn con. */
    @OneToMany(mappedBy = "parent")
    private List<Category> children;
}
