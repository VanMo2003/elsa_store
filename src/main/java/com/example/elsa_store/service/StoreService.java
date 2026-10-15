package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ShippingSettingsRequest;
import com.example.elsa_store.dto.request.StorePauseRequest;
import com.example.elsa_store.dto.request.StoreUpdateRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StoreService {
    /** Công khai: cửa hàng đã được duyệt. */
    PageResponse<StoreResponse> getAllPublic(int page, int size);

    /** Công khai: thông tin một cửa hàng đã được duyệt. */
    StoreResponse getPublic(Long id);

    /** Cửa hàng của chủ / quản lý / nhân viên đang đăng nhập. */
    StoreResponse getMine();

    /** Chủ hoặc quản lý sửa thông tin cửa hàng. */
    StoreResponse updateMine(StoreUpdateRequest req);

    /** Chủ hoặc quản lý bật / tắt tạm nghỉ bán. */
    StoreResponse updatePause(StorePauseRequest req);

    /** Chủ hoặc quản lý đặt phí giao hàng và mức miễn phí giao. */
    StoreResponse updateShipping(ShippingSettingsRequest req);

    /** Đổi ảnh đại diện (logo) cửa hàng của người gọi (chủ / quản lý). */
    StoreResponse updateImage(MultipartFile file);

    /** Gỡ ảnh đại diện cửa hàng. */
    StoreResponse removeImage();
}
