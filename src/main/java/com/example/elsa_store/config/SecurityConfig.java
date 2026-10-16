package com.example.elsa_store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.signer-key}")
    private String SIGNER_KEY;

    // Khách chưa đăng nhập vẫn xem được cửa hàng: danh mục, sản phẩm, biến thể, thông tin cửa hàng đã duyệt.
    // "/app-links/*": trang chuyển link trong email sang deep link elsastore:// của app.
    private final String[] PUBLIC_ENDPOINT_GET = {
        "/app-links/*",
        "/app-links/*/*",
        "/categories",
        "/categories/{id:\\d+}",
        "/products",
        "/products/{id:\\d+}",
        "/products/{id:\\d+}/related",
        "/products/{id:\\d+}/bought-together",
        // Đánh giá sản phẩm: ai cũng đọc được; "/me" trả "chưa đăng nhập" cho khách vãng lai.
        "/products/{id:\\d+}/reviews",
        "/products/{id:\\d+}/reviews/summary",
        "/products/{id:\\d+}/reviews/me",
        // Hỏi đáp sản phẩm: câu đã trả lời ai cũng đọc được.
        "/products/{id:\\d+}/questions",
        "/product-variants",
        "/product-variants/{id:\\d+}",
        "/stores",
        "/stores/{id:\\d+}",
        // Mã giảm giá đang chạy của cửa hàng: hiện ở trang sản phẩm cho cả khách chưa đăng nhập.
        "/promotions/coupons",
        // Gợi ý khi gõ ô tìm kiếm.
        "/search/suggestions",
        // Trang chủ: sản phẩm đang giảm giá, bán chạy.
        "/home/highlights",
        "/home/banners",
        "/home/for-you",
        // Cho app biết có hiện nút AI hay không (máy chủ chưa có khoá AI thì ẩn).
        "/ai/status",
        // Kết quả thanh toán VNPay (trình duyệt quay về và IPN máy chủ → máy chủ): xác thực bằng chữ ký VNPay.
        "/payment/vn-pay-callback",
        "/payment/vnpay-ipn"
    };
    // "/users": khách tự đăng ký; "/store-registrations": đăng ký mở cửa hàng (chờ ADMIN duyệt);
    // "/ai/ask": trợ lý mua sắm Elsa, khách chưa đăng nhập vẫn hỏi được (giới hạn lượt theo IP).
    private final String[] PUBLIC_ENDPOINT_POST = {
        "/users",
        "/store-registrations",
        "/auth/login",
        "/auth/introspect",
        "/auth/refreshToken",
        "/auth/password/forgot",
        "/auth/password/verify",
        "/auth/password/reset",
        "/products/search",
        "/ai/ask"
    };

    private final CustomJwtDecoder customJwtDecoder;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/uploads/**");
    }

    public SecurityConfig(CustomJwtDecoder customJwtDecoder) {
        this.customJwtDecoder = customJwtDecoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(request -> request.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINT_POST)
                .permitAll()
                // Socket chat tự xác thực lần nâng cấp bằng ?token= (ChatHandshakeInterceptor).
                .requestMatchers("/ws/chat")
                .permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINT_GET)
                .permitAll()
                .anyRequest()
                .authenticated());

        httpSecurity.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer
                        .decoder(customJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(SIGNER_KEY.getBytes(), "HS512");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}
