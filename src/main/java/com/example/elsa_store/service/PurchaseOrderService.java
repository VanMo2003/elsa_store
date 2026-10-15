package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.PurchaseOrderRequest;
import com.example.elsa_store.dto.response.PurchaseOrderResponse;

import java.util.List;

/**
 * Phiếu nhập hàng. Chủ và quản lý tạo phiếu nháp, sửa thoải mái, rồi bấm "nhập kho": hàng được cộng vào kho, giá vốn tính
 * lại theo bình quân gia quyền, mỗi dòng ghi thẻ kho. Phiếu đã nhập kho bị khoá (không sửa / huỷ / xoá). ADMIN chỉ xem.
 */
public interface PurchaseOrderService {

    /** {@code status} null = mọi trạng thái; {@code storeId} chỉ ADMIN dùng (null = toàn hệ thống). */
    List<PurchaseOrderResponse> list(Integer status, Long storeId);

    PurchaseOrderResponse get(Long id);

    PurchaseOrderResponse create(PurchaseOrderRequest request);

    PurchaseOrderResponse update(Long id, PurchaseOrderRequest request);

    PurchaseOrderResponse receive(Long id);

    PurchaseOrderResponse cancel(Long id);

    /** Chỉ xoá được phiếu nháp hoặc đã huỷ — phiếu đã nhập kho là chứng từ của thẻ kho. */
    void delete(Long id);
}
