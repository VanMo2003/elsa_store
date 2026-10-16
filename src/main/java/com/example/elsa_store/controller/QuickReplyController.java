package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.QuickReplyRequest;
import com.example.elsa_store.dto.response.QuickReplyResponse;
import com.example.elsa_store.service.QuickReplyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Câu trả lời nhanh trong chat của cửa hàng (chủ, quản lý, nhân viên). */
@RestController
@RequestMapping("/chat/quick-replies")
public class QuickReplyController {

    private final QuickReplyService service;

    public QuickReplyController(QuickReplyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<QuickReplyResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<QuickReplyResponse> create(@Valid @RequestBody QuickReplyRequest body) {
        return ApiResponse.ok(service.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuickReplyResponse> update(@PathVariable Long id, @Valid @RequestBody QuickReplyRequest body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
