package com.example.elsa_store.config;

import com.example.elsa_store.dto.request.IntrospectRequest;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.impl.AuthenticationServiceImpl;
import com.nimbusds.jose.JOSEException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;

/**
 * Giải mã access token cho mọi API (và lần nâng cấp socket chat): chữ ký, hạn dùng, danh sách thu hồi và trạng thái
 * tài khoản do {@link AuthenticationService#introspect} kiểm tra. Refresh token (sống 7 ngày) bị từ chối ở đây — nó
 * chỉ dùng để đổi lấy cặp token mới qua {@code /auth/refreshToken}. Lỗi ném ra là {@link BadJwtException} để Spring
 * Security trả 401 (app dựa vào 401 để tự làm mới phiên).
 */
@Component
public class CustomJwtDecoder implements JwtDecoder {

    private final AuthenticationService authenticationService;
    private final NimbusJwtDecoder nimbusJwtDecoder;

    public CustomJwtDecoder(
            AuthenticationService authenticationService, @Value("${jwt.signer-key}") String signerKey) {
        this.authenticationService = authenticationService;
        this.nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(signerKey.getBytes(), "HS512"))
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            var response = authenticationService.introspect(
                    IntrospectRequest.builder().token(token).build());
            if (!response.isValid()) {
                throw new BadJwtException("Token is invalid, expired or revoked");
            }
        } catch (JOSEException | ParseException e) {
            throw new BadJwtException(e.getMessage(), e);
        }

        Jwt jwt = nimbusJwtDecoder.decode(token);
        if (AuthenticationServiceImpl.REFRESH.equals(jwt.getClaimAsString(AuthenticationServiceImpl.TOKEN_TYPE_CLAIM))) {
            throw new BadJwtException("A refresh token cannot be used to call the API");
        }
        return jwt;
    }
}
