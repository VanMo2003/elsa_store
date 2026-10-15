package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.AuthenticationRequest;
import com.example.elsa_store.dto.request.IntrospectRequest;
import com.example.elsa_store.dto.request.RefreshTokenRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.IntrospectResponse;
import com.example.elsa_store.entity.User;
import com.nimbusds.jose.JOSEException;

import java.text.ParseException;

public interface AuthenticationService {
    IntrospectResponse introspect(IntrospectRequest request) throws ParseException, JOSEException;

    /** Đăng nhập; {@code clientIp} dùng để chặn dò mật khẩu từ một địa chỉ. */
    AuthenticationResponse authenticated(AuthenticationRequest request, String clientIp);

    /** Cấp cặp token mới cho tài khoản (đăng nhập, hoặc sau khi đổi mật khẩu để thiết bị hiện tại không bị đăng xuất). */
    AuthenticationResponse issueTokens(User user);

    void logout() throws ParseException, JOSEException;

    AuthenticationResponse refreshToken(RefreshTokenRequest request) throws ParseException, JOSEException;
}
