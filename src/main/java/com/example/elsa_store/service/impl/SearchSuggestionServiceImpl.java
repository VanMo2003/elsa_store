package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.SearchSuggestionResponse;
import com.example.elsa_store.dto.response.SearchSuggestionResponse.StoreSuggestion;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.service.SearchSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Gợi ý tìm kiếm công khai: chỉ sản phẩm đang bán và cửa hàng đã duyệt. So khớp như tìm kiếm (chứa từ khoá, không phân
 * biệt hoa thường; collation của MySQL bỏ qua dấu nên "giay" khớp "Giày").
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchSuggestionServiceImpl implements SearchSuggestionService {

    static final int MIN_LENGTH = 2;
    static final int MAX_STORES = 3;

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    @Override
    public SearchSuggestionResponse suggest(String keyword, int size) {
        String text = keyword == null ? "" : keyword.strip();
        if (text.length() < MIN_LENGTH) {
            return SearchSuggestionResponse.empty();
        }
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        int limit = Math.min(Math.max(size, 1), 10);
        var keywords = productRepository.suggestNames(pattern, PageRequest.of(0, limit));
        var stores = storeRepository.suggest(pattern, PageRequest.of(0, MAX_STORES)).stream()
                .map(s -> new StoreSuggestion(s.getId(), s.getName(), s.getImageUrl()))
                .toList();
        return new SearchSuggestionResponse(keywords, stores);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
