package com.example.elsa_store.service.impl;

import com.example.elsa_store.config.CustomJwtDecoder;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.AuthenticationRequest;
import com.example.elsa_store.dto.request.IntrospectRequest;
import com.example.elsa_store.dto.request.RefreshTokenRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.InvalidatedRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationServiceImplTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final UserRepository users = mock(UserRepository.class);
    private final InvalidatedRepository revoked = mock(InvalidatedRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthenticationServiceImpl service = new AuthenticationServiceImpl(
            3600, 604800, KEY, users, revoked, mock(AccessGuard.class), encoder, new LoginAttemptLimiter());
    private final CustomJwtDecoder decoder = new CustomJwtDecoder(service, KEY);

    private User user;
    private boolean enabled;
    private int version;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("khach001")
                .password(encoder.encode("123456"))
                .role(Role.USER)
                .build();
        user.setId(7L);
        enabled = true;
        version = 0;
        when(users.findByUsername("khach001")).thenAnswer(inv -> Optional.of(user));
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());
        when(users.findAuthState("khach001")).thenAnswer(inv -> Optional.of(new UserRepository.AuthState() {
            @Override
            public Boolean getEnabled() {
                return enabled;
            }

            @Override
            public Integer getTokenVersion() {
                return version;
            }
        }));
        when(revoked.existsById(anyString())).thenReturn(false);
    }

    private AuthenticationResponse login(String username, String password) {
        return service.authenticated(new AuthenticationRequest(username, password), "10.0.0.1");
    }

    private boolean valid(String token) throws Exception {
        return service.introspect(IntrospectRequest.builder().token(token).build()).isValid();
    }

    @Test
    void accessTokenWorksButTheRefreshTokenCannotCallTheApi() {
        AuthenticationResponse tokens = login("khach001", "123456");

        Jwt jwt = decoder.decode(tokens.getAccessToken());
        assertEquals("khach001", jwt.getSubject());
        assertThrows(BadJwtException.class, () -> decoder.decode(tokens.getRefreshToken()));
    }

    @Test
    void wrongPasswordAndUnknownAccountGiveTheSameError() {
        AppException wrong = assertThrows(AppException.class, () -> login("khach001", "nope"));
        AppException unknown = assertThrows(AppException.class, () -> login("nobody", "123456"));
        assertEquals(ErrorCode.INCORRECT_ACCOUNT_OR_PASSWORD, wrong.getErrorCode());
        assertEquals(ErrorCode.INCORRECT_ACCOUNT_OR_PASSWORD, unknown.getErrorCode());
    }

    @Test
    void fiveWrongPasswordsLockLoginEvenWithTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            assertThrows(AppException.class, () -> login("khach001", "guess"));
        }
        AppException ex = assertThrows(AppException.class, () -> login("khach001", "123456"));
        assertEquals(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, ex.getErrorCode());
    }

    @Test
    void lockedAccountCannotLogInAndItsTokensStopWorking() throws Exception {
        String token = login("khach001", "123456").getAccessToken();
        assertTrue(valid(token));

        enabled = false;
        assertFalse(valid(token));
        assertThrows(BadJwtException.class, () -> decoder.decode(token));
        user.setEnabled(false);
        AppException ex = assertThrows(AppException.class, () -> login("khach001", "123456"));
        assertEquals("Account is locked", ex.getMessage());
    }

    @Test
    void changingThePasswordRevokesEveryEarlierTokenEvenWithinTheSameSecond() throws Exception {
        String before = login("khach001", "123456").getAccessToken();

        // Đổi mật khẩu ngay trong cùng giây với lúc cấp token cũ: token cũ vẫn phải bị từ chối.
        user.markCredentialsChanged();
        version = user.currentTokenVersion();
        assertFalse(valid(before));
        assertThrows(BadJwtException.class, () -> decoder.decode(before));

        // Token cấp ngay sau khi đổi (cùng giây) vẫn dùng được.
        String after = service.issueTokens(user).getAccessToken();
        assertTrue(valid(after));
    }

    @Test
    void tokensWithoutAVersionCountAsVersionZero() throws Exception {
        String token = login("khach001", "123456").getAccessToken();
        assertTrue(valid(token));
        version = 3;
        assertFalse(valid(token));
    }

    @Test
    void garbageTokensAreUnauthenticatedNotServerErrors() throws Exception {
        assertFalse(valid("not-a-jwt"));
        assertFalse(valid(""));
        AppException ex = assertThrows(
                AppException.class, () -> service.refreshToken(new RefreshTokenRequest("not-a-jwt")));
        assertEquals(ErrorCode.UN_AUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void refreshRotatesTheTokens() throws Exception {
        AuthenticationResponse first = login("khach001", "123456");
        AuthenticationResponse second = service.refreshToken(new RefreshTokenRequest(first.getRefreshToken()));
        assertNotNull(second.getAccessToken());
        assertEquals("khach001", decoder.decode(second.getAccessToken()).getSubject());
    }
}
