package com.example.elsa_store.constant;

/** Lý do khách báo cáo một sản phẩm vi phạm. */
public enum ViolationReason {
    /** Hàng giả, hàng nhái. */
    FAKE,
    /** Mô tả / hình ảnh sai sự thật. */
    WRONG_DESCRIPTION,
    /** Lừa đảo, yêu cầu giao dịch ngoài sàn. */
    SCAM,
    /** Nội dung phản cảm, cấm bán. */
    PROHIBITED,
    OTHER
}
