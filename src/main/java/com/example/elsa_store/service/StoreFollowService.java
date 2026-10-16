package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;

/** Khách theo dõi cửa hàng (chỉ tài khoản USER). */
public interface StoreFollowService {

    /** Theo dõi (bấm lại không đổi gì); trả trang cửa hàng với {@code following = true}. */
    StoreResponse follow(Long storeId);

    StoreResponse unfollow(Long storeId);

    /** Cửa hàng đang theo dõi, theo dõi gần nhất trước. */
    PageResponse<StoreResponse> following(int page, int size);
}
