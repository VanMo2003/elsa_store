package com.example.elsa_store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/** Tạo / sửa phiếu nhập (chỉ khi còn nháp). Mỗi biến thể chỉ một dòng. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderRequest {

    /** Nhà cung cấp của chính cửa hàng; bỏ trống được. */
    private Long supplierId;

    @Size(max = 500)
    private String note;

    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<Line> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Line {
        @NotNull
        private Long productVariantId;

        @NotNull
        @Min(1)
        @Max(100_000)
        private Integer quantity;

        /** Giá nhập một đơn vị. */
        @NotNull
        @PositiveOrZero
        @Max(10_000_000_000L)
        private Double unitPrice;
    }
}
