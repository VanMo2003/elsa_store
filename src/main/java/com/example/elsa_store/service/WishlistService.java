package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.ProductResponse;

import java.util.List;

/** Danh sách yêu thích của khách: chỉ tài khoản khách hàng; sản phẩm đã ẩn / cửa hàng chưa mở không hiện. */
public interface WishlistService {

    List<ProductResponse> list();

    List<Long> productIds();

    /** Thêm (bấm lại không lỗi). Trả danh sách id sau khi thêm. */
    List<Long> add(Long productId);

    List<Long> remove(Long productId);
}
