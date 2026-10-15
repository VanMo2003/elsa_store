package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.AuthenticationRequest;
import com.example.elsa_store.dto.request.IntrospectRequest;
import com.example.elsa_store.dto.request.RefreshTokenRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.IntrospectResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.mapper.StoreMapper;
import com.example.elsa_store.mapper.UserMapper;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.LoginAttemptLimiter;
import com.example.elsa_store.service.AuthenticationService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    public static final String TOKEN_TYPE_CLAIM = "token_type";
    /** Phiên bản token của tài khoản lúc cấp ({@code User.tokenVersion}). */
    public static final String VERSION_CLAIM = "ver";
    public static final String ACCESS = "ACCESS";
    public static final String REFRESH = "REFRESH";

    private final long validDuration;
    private final long refreshableDuration;
    private final String signerKey;
    private final UserRepository userRepository;
    private final InvalidatedRepository invalidatedRepository;
    private final AccessGuard accessGuard;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    /** So mật khẩu với băm giả khi tài khoản không tồn tại, để thời gian phản hồi không lộ tên đăng nhập nào có thật. */
    private final String dummyHash;

    public AuthenticationServiceImpl(
            @Value("${jwt.valid-duration}") long validDuration,
            @Value("${jwt.refreshable-duration}") long refreshableDuration,
            @Value("${jwt.signer-key}") String signerKey,
            UserRepository userRepository,
            InvalidatedRepository invalidatedRepository,
            AccessGuard accessGuard,
            PasswordEncoder passwordEncoder,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.validDuration = validDuration;
        this.refreshableDuration = refreshableDuration;
        this.signerKey = signerKey;
        this.userRepository = userRepository;
        this.invalidatedRepository = invalidatedRepository;
        this.accessGuard = accessGuard;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public IntrospectResponse introspect(IntrospectRequest request) throws ParseException, JOSEException {
        boolean isValid = true;
        try {
            verifyToken(request.getToken());
        } catch (AppException | ParseException e) {
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).build();
    }

    @Override
    public AuthenticationResponse authenticated(AuthenticationRequest request, String clientIp) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        loginAttemptLimiter.checkAllowed(username, clientIp);

        User user = userRepository.findByUsername(username).orElse(null);
        String password = request.getPassword() == null ? "" : request.getPassword();
        boolean authenticated = passwordEncoder.matches(password, user == null ? dummyHash : user.getPassword());
        if (user == null || !authenticated) {
            loginAttemptLimiter.recordFailure(username, clientIp);
            throw new AppException(ErrorCode.INCORRECT_ACCOUNT_OR_PASSWORD);
        }
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Account is locked");
        }
        loginAttemptLimiter.recordSuccess(username);
        return issueTokens(user);
    }

    @Override
    public AuthenticationResponse issueTokens(User user) {
        String jit = UUID.randomUUID().toString();
        AuthenticationResponse response = AuthenticationResponse.builder()
                .accessToken(generateToken(user, ACCESS, jit))
                .refreshToken(generateToken(user, REFRESH, jit))
                .authenticated(true)
                .role(user.getRole())
                .build();
        Store store = accessGuard.storeOf(user);
        response.setUser(UserMapper.toResponse(user, store));
        response.setStore(StoreMapper.toResponse(store));
        return response;
    }

    @Override
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) throws ParseException, JOSEException {
        SignedJWT signJWT = verifyToken(request.getToken());
        var claims = signJWT.getJWTClaimsSet();
        if (!REFRESH.equals(claims.getStringClaim(TOKEN_TYPE_CLAIM))) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }

        // Mỗi refresh token chỉ dùng một lần: thu hồi cặp cũ (access và refresh chung jti).
        invalidatedRepository.save(InvalidatedToken.builder()
                .id(claims.getJWTID())
                .expiryTime(claims.getExpirationTime())
                .build());

        User user = userRepository.findByUsername(claims.getSubject())
                .filter(User::isEnabled)
                .orElseThrow(() -> new AppException(ErrorCode.UN_AUTHENTICATED));

        String jitNew = UUID.randomUUID().toString();
        return AuthenticationResponse.builder()
                .accessToken(generateToken(user, ACCESS, jitNew))
                .refreshToken(generateToken(user, REFRESH, jitNew))
                .authenticated(true)
                .build();
    }

    @Override
    public void logout() throws ParseException, JOSEException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        Jwt jwt = jwtAuth.getToken();
        SignedJWT signedJWT = verifyToken(jwt.getTokenValue());
        invalidatedRepository.save(InvalidatedToken.builder()
                .id(signedJWT.getJWTClaimsSet().getJWTID())
                .expiryTime(signedJWT.getJWTClaimsSet().getExpirationTime())
                .build());
    }

    /** Token đã thu hồi nhưng hết hạn thì tự vô hiệu — xoá khỏi bảng mỗi giờ để bảng không phình mãi. */
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void purgeExpiredInvalidatedTokens() {
        int removed = invalidatedRepository.deleteExpired(new Date());
        if (removed > 0) {
            log.debug("Removed {} expired revoked tokens", removed);
        }
    }

    private SignedJWT verifyToken(String token) throws ParseException, JOSEException {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            // Chuỗi rác thay cho token: là lỗi đăng nhập (401), không phải lỗi máy chủ.
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        if (!signedJWT.verify(new MACVerifier(signerKey.getBytes()))) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        Date expiryTime = claims.getExpirationTime();
        if (expiryTime == null || expiryTime.before(new Date())) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        if (claims.getJWTID() == null || invalidatedRepository.existsById(claims.getJWTID())) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        requireUsableAccount(claims);
        return signedJWT;
    }

    /**
     * Tài khoản bị khoá, bị xoá, hoặc đã đổi mật khẩu sau khi token được cấp thì token không còn dùng được — có hiệu lực
     * ngay ở mọi API và cả socket chat, không phải chờ token hết hạn. Token cấp trước khi có cơ chế này không mang
     * {@code ver}, được coi là phiên bản 0.
     */
    private void requireUsableAccount(JWTClaimsSet claims) throws ParseException {
        UserRepository.AuthState state = userRepository
                .findAuthState(claims.getSubject())
                .orElseThrow(() -> new AppException(ErrorCode.UN_AUTHENTICATED));
        if (!Boolean.TRUE.equals(state.getEnabled())) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        Long tokenVersion = claims.getLongClaim(VERSION_CLAIM);
        long current = state.getTokenVersion() == null ? 0 : state.getTokenVersion();
        if ((tokenVersion == null ? 0 : tokenVersion) != current) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
    }

    private String generateToken(User user, String tokenType, String jit) {
        long duration = ACCESS.equals(tokenType) ? validDuration : refreshableDuration;
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("vanmo.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(duration, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(jit)
                .claim("scope", buildScope(user))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim(VERSION_CLAIM, user.currentTokenVersion())
                .build();
        try {
            JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload(jwtClaimsSet.toJSONObject()));
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        if (user.getRole() != null) {
            stringJoiner.add("ROLE_" + user.getRole());
        }
        return stringJoiner.toString();
    }
}
