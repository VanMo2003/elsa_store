
package com.example.elsa_store.controller;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ChangePasswordRequest;
import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.request.UserUpdateRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Đăng ký công khai cho khách (vai trò USER). */
    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserRequest req) {
        return ApiResponse.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UserUpdateRequest req) {
        return ApiResponse.ok(userService.update(id, req));
    }

    /** Đổi mật khẩu của chính mình: cần mật khẩu hiện tại; trả cặp token mới, các thiết bị khác bị đăng xuất. */
    @PutMapping("/me/password")
    public ApiResponse<AuthenticationResponse> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return ApiResponse.ok(userService.changePassword(req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok(null);
    }

    /** Ảnh đại diện của chính mình: multipart, phần {@code file}. */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> updateAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userService.updateAvatar(file));
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserResponse> removeAvatar() {
        return ApiResponse.ok(userService.removeAvatar());
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe() {
        return ApiResponse.ok(userService.getMe());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    /** Danh sách tài khoản — chỉ ADMIN. */
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> getAll(
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.getAll(role, page, size));
    }
}
