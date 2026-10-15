package com.example.elsa_store.dto.response;

import lombok.*;

/** Tổng quan kho của cửa hàng. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockOverviewResponse {
    private Long totalVariants;
    private Long untrackedVariants;
    private Long outOfStockVariants;
    private Long lowStockVariants;
    /** Tổng số đơn vị hàng đang có. */
    private Long totalUnits;
    /** Giá trị tồn theo giá vốn (chỉ các biến thể đã có giá vốn). */
    private Double stockValue;
    /** Số biến thể còn hàng mà chưa có giá vốn — giá trị tồn chưa đầy đủ. */
    private Long uncostedVariants;
    /** Giá trị tồn theo giá bán. */
    private Double retailValue;

    /** Từ một dòng của {@code ProductVariantRepository.stockOverview}. */
    public static StockOverviewResponse fromRow(Object[] r) {
        return StockOverviewResponse.builder()
                .totalVariants(asLong(r[0]))
                .untrackedVariants(asLong(r[1]))
                .outOfStockVariants(asLong(r[2]))
                .lowStockVariants(asLong(r[3]))
                .totalUnits(asLong(r[4]))
                .stockValue(asDouble(r[5]))
                .uncostedVariants(asLong(r[6]))
                .retailValue(asDouble(r[7]))
                .build();
    }

    public static StockOverviewResponse empty() {
        return fromRow(new Object[8]);
    }

    private static long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double asDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
