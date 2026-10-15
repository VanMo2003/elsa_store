package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.SearchSuggestionResponse;
import com.example.elsa_store.service.SearchSuggestionService;
import org.springframework.web.bind.annotation.*;

/** Gợi ý khi gõ ô tìm kiếm (công khai). */
@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchSuggestionService suggestionService;

    public SearchController(SearchSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/suggestions")
    public ApiResponse<SearchSuggestionResponse> suggestions(@RequestParam(defaultValue = "") String keyword,
                                                             @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(suggestionService.suggest(keyword, size));
    }
}
