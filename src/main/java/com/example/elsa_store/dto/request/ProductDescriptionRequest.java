package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nhờ AI viết mô tả sản phẩm.
 *
 * @param name tên sản phẩm
 * @param categoryId danh mục đang chọn (nếu có)
 * @param productId sản phẩm đang sửa: lấy thêm màu / cỡ của các phân loại
 * @param price giá đang nhập (nếu có)
 * @param notes gợi ý người bán gõ sẵn trong ô mô tả (chất liệu, kiểu dáng, dịp dùng…)
 */
public record ProductDescriptionRequest(
        @NotBlank @Size(max = 200) String name,
        Long categoryId,
        Long productId,
        @DecimalMin("0") Double price,
        @Size(max = 2000) String notes) {}
