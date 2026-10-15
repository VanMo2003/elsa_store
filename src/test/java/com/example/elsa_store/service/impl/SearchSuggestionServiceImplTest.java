package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.SearchSuggestionResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SearchSuggestionServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final SearchSuggestionServiceImpl service = new SearchSuggestionServiceImpl(products, stores);

    @Test
    void shortKeywordsGetNoSuggestionsAndNoQuery() {
        assertEquals(SearchSuggestionResponse.empty(), service.suggest(" g ", 8));
        assertEquals(SearchSuggestionResponse.empty(), service.suggest(null, 8));
        verifyNoInteractions(products, stores);
    }

    @Test
    void namesAndStoresMatchTheEscapedLowercasePattern() {
        Store store = Store.builder().name("Giày Elsa").imageUrl("/uploads/stores/a.png").build();
        store.setId(3L);
        String pattern = "%giày\\_100\\%%";
        when(products.suggestNames(eq(pattern), any())).thenReturn(List.of("Giày chạy bộ"));
        when(stores.suggest(eq(pattern), any())).thenReturn(List.of(store));

        SearchSuggestionResponse res = service.suggest("  GIÀY_100%  ", 50);
        assertEquals(List.of("Giày chạy bộ"), res.keywords());
        assertEquals(List.of(new SearchSuggestionResponse.StoreSuggestion(3L, "Giày Elsa", "/uploads/stores/a.png")),
                res.stores());
        verify(products).suggestNames(any(), argThat((Pageable p) -> p.getPageSize() == 10));
        verify(stores).suggest(any(), argThat((Pageable p) -> p.getPageSize() == SearchSuggestionServiceImpl.MAX_STORES));
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertEquals("50\\%\\_off\\\\", SearchSuggestionServiceImpl.escapeLike("50%_off\\"));
    }
}
