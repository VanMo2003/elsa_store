package com.example.elsa_store.dto.response;

/**
 * Mô tả sản phẩm gợi ý (người bán đọc, sửa rồi mới lưu).
 *
 * @param ai true = mô hình AI viết; false = mẫu soạn sẵn từ thông tin đã nhập
 * @param notice lý do dùng mẫu soạn sẵn (null khi AI viết)
 */
public record ProductDescriptionResponse(String description, boolean ai, String notice) {}
