package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopCustomerResponse {
    private Long customerId;
    private String name;
    private String phone;
    private Long ordersCount;
    private Double totalSpent;
}
