
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    /** Tài khoản của khách; null với khách vãng lai do nhân viên tạo tại quầy. */
    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    /** Cửa hàng đã tạo hồ sơ khách vãng lai này. */
    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "gender")
    private Integer gender; // 0: other, 1: male, 2: female

    @Column(name = "dob")
    private LocalDate dob;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Address> addresses;
}
