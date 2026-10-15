package com.example.elsa_store.dto.response;

import java.util.List;

/** Hai dải ở trang chủ: sản phẩm đang giảm giá (giảm nhiều trước) và bán chạy. */
public record HomeHighlightsResponse(List<ProductResponse> onSale, List<ProductResponse> bestSellers) {}
