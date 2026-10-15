package com.example.elsa_store.dto.response;

import java.util.List;

/** Gợi ý khi khách đang gõ ô tìm kiếm: tên sản phẩm khớp và vài cửa hàng khớp tên. */
public record SearchSuggestionResponse(List<String> keywords, List<StoreSuggestion> stores) {

    public record StoreSuggestion(Long id, String name, String imageUrl) {}

    public static SearchSuggestionResponse empty() {
        return new SearchSuggestionResponse(List.of(), List.of());
    }
}
