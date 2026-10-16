package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.AnswerRequest;
import com.example.elsa_store.dto.request.QuestionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.QuestionResponse;

/**
 * Hỏi đáp về sản phẩm: khách đặt câu hỏi, người của cửa hàng trả lời. Câu đã trả lời ai cũng đọc được; câu chưa trả lời
 * chỉ người hỏi và cửa hàng thấy.
 */
public interface ProductQuestionService {

    PageResponse<QuestionResponse> list(Long productId, int page, int size);

    QuestionResponse ask(Long productId, QuestionRequest request);

    /** Người hỏi xoá câu của mình; người của cửa hàng hoặc ADMIN gỡ câu hỏi không phù hợp. */
    void delete(Long questionId);

    PageResponse<QuestionResponse> storeQuestions(boolean unanswered, int page, int size);

    QuestionResponse answer(Long questionId, AnswerRequest request);
}
