package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationRateResponse {
    private LocalDate from;
    private LocalDate to;
    private Long totalOrders;
    private Long cancelledOrders;
    /** Tỉ lệ huỷ, 0..1. */
    private Double rate;
}
