package com.example.elsa_store.constant;

/** Loại biến động trên thẻ kho. */
public enum StockMovementType {
    /** Nhập hàng từ phiếu nhập. */
    IMPORT,
    /** Bán hàng: trừ khi đặt đơn. */
    SALE,
    /** Hoàn kho: đơn bị huỷ / xoá trước khi giao. */
    RETURN,
    /** Kiểm kê / điều chỉnh tay, bắt đầu hoặc ngừng theo dõi kho. */
    ADJUST
}
