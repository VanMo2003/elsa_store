package com.example.elsa_store.service;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ChangePasswordRequest;
import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.request.UserUpdateRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    /** Khách tự đăng ký (vai trò USER) — công khai. */
    UserResponse create(UserRequest req);

    UserResponse update(Long id, UserUpdateRequest req);

    /** Đổi mật khẩu của chính mình (cần mật khẩu hiện tại); trả cặp token mới, các thiết bị khác bị đăng xuất. */
    AuthenticationResponse changePassword(ChangePasswordRequest req);

    /** Chỉ ADMIN. */
    void delete(Long id);

    /** Chính chủ hoặc ADMIN. */
    UserResponse getById(Long id);

    /** Tài khoản đang đăng nhập. */
    UserResponse getMe();

    /** Đổi ảnh đại diện của chính mình (mọi vai trò). */
    UserResponse updateAvatar(MultipartFile file);

    /** Gỡ ảnh đại diện của chính mình. */
    UserResponse removeAvatar();

    /** Chỉ ADMIN; lọc theo vai trò nếu có. */
    PageResponse<UserResponse> getAll(Role role, int page, int size);
}
