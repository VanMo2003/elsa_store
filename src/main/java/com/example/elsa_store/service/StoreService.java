package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.StoreUpdateRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;

public interface StoreService {
    /** Công khai: cửa hàng đã được duyệt. */
    PageResponse<StoreResponse> getAllPublic(int page, int size);

    /** Công khai: thông tin một cửa hàng đã được duyệt. */
    StoreResponse getPublic(Long id);

    /** Cửa hàng của chủ / quản lý / nhân viên đang đăng nhập. */
    StoreResponse getMine();

    /** Chủ hoặc quản lý sửa thông tin cửa hàng. */
    StoreResponse updateMine(StoreUpdateRequest req);
}
