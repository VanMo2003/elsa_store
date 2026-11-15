package com.example.elsa_store.dto.response;

import java.util.List;

/**
 * Kết quả "Mua lại".
 *
 * @param cart giỏ hàng sau khi thêm
 * @param added số món đã thêm (kể cả món chỉ thêm được một phần)
 * @param skipped món bỏ qua hoặc thêm thiếu, kèm lý do, để app báo khách
 */
public record ReorderResponse(CartResponse cart, int added, List<String> skipped) {}
