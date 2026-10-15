package com.example.elsa_store.constant;

/**
 * Yêu cầu trả hàng: chờ cửa hàng duyệt → đã duyệt (khách gửi hàng về) → đã hoàn tiền (hàng về kho); hoặc bị từ chối /
 * khách tự huỷ khi còn chờ duyệt. Giá trị mới luôn thêm vào cuối.
 */
public enum ReturnStatus {
    CHO_DUYET,
    DA_DUYET,
    TU_CHOI,
    DA_HOAN_TIEN,
    DA_HUY
}
