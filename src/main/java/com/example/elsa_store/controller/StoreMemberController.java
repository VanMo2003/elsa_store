package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.StoreMemberRequest;
import com.example.elsa_store.dto.request.StoreMemberUpdateRequest;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.service.StoreMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tài khoản quản lý và nhân viên của cửa hàng mà người gọi làm chủ hoặc quản lý. */
@RestController
@RequestMapping("/store-members")
@RequiredArgsConstructor
public class StoreMemberController {

    private final StoreMemberService storeMemberService;

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody StoreMemberRequest req) {
        return ApiResponse.ok(storeMemberService.create(req));
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> getAll() {
        return ApiResponse.ok(storeMemberService.getAll());
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id, @Valid @RequestBody StoreMemberUpdateRequest req) {
        return ApiResponse.ok(storeMemberService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        storeMemberService.delete(id);
        return ApiResponse.ok(null);
    }
}
