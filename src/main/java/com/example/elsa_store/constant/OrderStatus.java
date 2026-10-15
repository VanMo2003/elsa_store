package com.example.elsa_store.constant;

/**
 * Trạng thái đơn: chờ xác nhận → đã xác nhận → (đang giao) → hoàn thành, hoặc huỷ. Giá trị mới luôn thêm vào
 * <b>cuối</b>: báo cáo vẫn nhận số thứ tự cũ của app (0 = CHUA_XAC_NHAN…).
 */
public enum OrderStatus {
    CHUA_XAC_NHAN,
    DA_XAC_NHAN,
    DA_HUY,
    HOAN_THANH,
    /** Đã giao cho đơn vị vận chuyển; đơn bán tại quầy (không có địa chỉ giao) bỏ qua bước này. */
    DANG_GIAO
}
