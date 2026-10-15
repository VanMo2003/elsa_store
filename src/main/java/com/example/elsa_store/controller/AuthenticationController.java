package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AuthenticationRequest;
import com.example.elsa_store.dto.request.IntrospectRequest;
import com.example.elsa_store.dto.request.PasswordResetRequest;
import com.example.elsa_store.dto.request.RefreshTokenRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.IntrospectResponse;
import com.example.elsa_store.dto.response.PasswordResetResponse;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.PasswordResetService;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {
    AuthenticationService authenticationService;
    PasswordResetService passwordResetService;

    /**
     * Sai mật khẩu 5 lần cho một tài khoản (hoặc 20 lần từ một IP) trong 15 phút thì bị tạm chặn: 429, mã 1021. IP lấy
     * từ kết nối TCP, không từ header X-Forwarded-For (ai cũng giả được header đó).
     */
    @PostMapping("/login")
    ApiResponse<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request, HttpServletRequest http) {
        return ApiResponse.ok(authenticationService.authenticated(request, http.getRemoteAddr()));
    }

    /**
     * Quên mật khẩu — bước 1: gửi mã 6 số tới email của tài khoản ({@code identifier} = tên đăng nhập hoặc email). Luôn
     * trả cùng một kết quả để không lộ tài khoản nào tồn tại; tối đa 1 mã / phút, 5 mã / giờ / tài khoản.
     */
    @PostMapping("/password/forgot")
    ApiResponse<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        return ApiResponse.ok(passwordResetService.requestCode(request.getIdentifier(), http.getRemoteAddr()));
    }

    /** Bước 2: kiểm mã (không tiêu mã). Sai 5 lần thì mã hỏng, phải xin mã mới. */
    @PostMapping("/password/verify")
    ApiResponse<Void> verifyResetCode(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.verifyCode(request.getIdentifier(), request.getCode());
        return ApiResponse.ok(null);
    }

    /** Bước 3: đặt mật khẩu mới; mọi phiên đăng nhập cũ hết hiệu lực. */
    @PostMapping("/password/reset")
    ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request.getIdentifier(), request.getCode(), request.getNewPassword());
        return ApiResponse.ok(null);
    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        return ApiResponse.ok(authenticationService.introspect(request));
    }

    @PostMapping("/logout")
    ApiResponse<Boolean> logout() throws ParseException, JOSEException {
        authenticationService.logout();
        return ApiResponse.ok(true);
    }

    @PostMapping("/refreshToken")
    ApiResponse<AuthenticationResponse> refreshToken(@RequestBody RefreshTokenRequest request)
            throws ParseException, JOSEException {
        return ApiResponse.ok(authenticationService.refreshToken(request));
    }
}
