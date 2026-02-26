
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "inventories",
        indexes = {
                @Index(name = "idx_inventory_variant", columnList = "product_variant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "product_variant_id", nullable = false, unique = true)
    private ProductVariant productVariant;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "min_quantity")
    private Integer minQuantity;
}
