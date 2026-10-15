package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/** Doanh thu tách theo cách trả tiền (COD / VNPAY) và kênh bán (ONLINE / COUNTER). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueBreakdownResponse {
    private LocalDate from;
    private LocalDate to;
    private List<Slice> byPaymentMethod;
    private List<Slice> byChannel;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Slice {
        /** COD, VNPAY, OTHER — hoặc ONLINE, COUNTER. */
        private String key;
        private Long ordersCount;
        private Double revenue;
        /** Tỷ trọng doanh thu, phần trăm. */
        private Double share;
    }
}
