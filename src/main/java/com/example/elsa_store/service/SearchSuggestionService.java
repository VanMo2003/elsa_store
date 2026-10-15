package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.SearchSuggestionResponse;

public interface SearchSuggestionService {

    /** Từ khoá dưới 2 ký tự thì không gợi ý. {@code size}: số tên sản phẩm tối đa (1–10). */
    SearchSuggestionResponse suggest(String keyword, int size);
}
