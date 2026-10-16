package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AnswerRequest;
import com.example.elsa_store.dto.request.QuestionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.QuestionResponse;
import com.example.elsa_store.service.ProductQuestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Hỏi đáp sản phẩm: đọc công khai; khách hỏi / xoá câu của mình; người của cửa hàng xem và trả lời. */
@RestController
public class ProductQuestionController {

    private final ProductQuestionService questionService;

    public ProductQuestionController(ProductQuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/products/{productId}/questions")
    public ApiResponse<PageResponse<QuestionResponse>> list(@PathVariable Long productId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(questionService.list(productId, page, size));
    }

    @PostMapping("/products/{productId}/questions")
    public ApiResponse<QuestionResponse> ask(@PathVariable Long productId, @Valid @RequestBody QuestionRequest body) {
        return ApiResponse.ok(questionService.ask(productId, body));
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/questions/store")
    public ApiResponse<PageResponse<QuestionResponse>> storeQuestions(
            @RequestParam(defaultValue = "true") boolean unanswered,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(questionService.storeQuestions(unanswered, page, size));
    }

    @PutMapping("/questions/{id}/answer")
    public ApiResponse<QuestionResponse> answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest body) {
        return ApiResponse.ok(questionService.answer(id, body));
    }
}
