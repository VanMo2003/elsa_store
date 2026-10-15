
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "type")
    private Integer type;

    @Column(name = "value")
    private Double value;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    private Integer status;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "min_order_value")
    private Double minOrderValue;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL)
    private List<PromotionProduct> promotionProducts;
}
