package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.HomeHighlightsResponse;

public interface HomeHighlightsService {

    /** {@code size}: số sản phẩm tối đa mỗi dải (1–20). */
    HomeHighlightsResponse highlights(int size);
}
