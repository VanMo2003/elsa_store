package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Kiểm kê / chỉnh kho một biến thể. Trường nào để trống thì giữ nguyên. Biến thể chưa theo dõi kho phải nhập
 * {@code quantity} để bắt đầu theo dõi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustRequest {

    /** Số lượng đếm thực tế. */
    @Min(0)
    @Max(1_000_000)
    private Integer quantity;

    /** Mức cảnh báo sắp hết hàng. */
    @Min(0)
    @Max(1_000_000)
    private Integer minQuantity;

    /** Giá vốn một đơn vị. */
    @PositiveOrZero
    @Max(10_000_000_000L)
    private Double costPrice;

    /** Lý do điều chỉnh (ghi vào thẻ kho). */
    @Size(max = 255)
    private String note;
}
