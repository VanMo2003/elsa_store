package com.example.elsa_store.dto.response;

/**
 * Bản nháp do AI soạn để người của cửa hàng đọc, sửa rồi mới gửi (trả lời đánh giá, trả lời câu hỏi…).
 *
 * @param ai true = mô hình AI viết; false = mẫu soạn sẵn
 * @param notice lý do dùng mẫu soạn sẵn (null khi AI viết)
 */
public record AiDraftResponse(String text, boolean ai, String notice) {}
