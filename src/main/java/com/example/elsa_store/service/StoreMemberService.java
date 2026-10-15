package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.StoreMemberRequest;
import com.example.elsa_store.dto.request.StoreMemberUpdateRequest;
import com.example.elsa_store.dto.response.UserResponse;

import java.util.List;

/**
 * Tài khoản quản lý và nhân viên của cửa hàng. Chủ cửa hàng tạo được quản lý và nhân viên;
 * quản lý chỉ tạo được nhân viên.
 */
public interface StoreMemberService {
    UserResponse create(StoreMemberRequest req);

    List<UserResponse> getAll();

    UserResponse update(Long id, StoreMemberUpdateRequest req);

    /** Xoá nếu chưa có đơn nào gắn với tài khoản, ngược lại khoá tài khoản. */
    void delete(Long id);
}
