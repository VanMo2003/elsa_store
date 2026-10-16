package com.example.elsa_store.controller;

import com.example.elsa_store.utils.AppLinks;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Trang đích cho liên kết trong email. Ứng dụng mail bỏ qua liên kết custom-scheme nên email trỏ về đây qua
 * http(s), rồi trang này chuyển điện thoại sang deep link {@code elsastore://} của app.
 */
@RestController
@RequestMapping("/app-links")
public class AppLinkController {

    private static final Set<String> TARGETS = Set.of(AppLinks.ORDERS, AppLinks.NOTIFICATIONS, AppLinks.STORE_STATUS);

    /** Đích có id — dùng cho nút "Chia sẻ" sản phẩm / cửa hàng trong app. */
    private static final Set<String> ITEM_TARGETS = Set.of("products", "stores");

    private static final String PAGE =
            """
            <!doctype html>
            <html lang="vi">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Mở Elsa Store</title>
            <style>
            body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
                    background: #F5F5F5; color: #212121;
                    font-family: system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif; }
            main { background: #FFFFFF; border-radius: 20px; padding: 32px 28px; margin: 24px; max-width: 360px;
                    text-align: center; box-shadow: 0 8px 24px rgba(33, 33, 33, .08); }
            .mark { width: 64px; height: 64px; margin: 0 auto 18px; border-radius: 19px; background: #E64A19;
                    color: #FFFFFF; font-size: 28px; font-weight: 700; line-height: 64px; }
            h1 { margin: 0 0 8px; font-size: 20px; }
            p { margin: 0 0 22px; font-size: 14px; line-height: 21px; color: #616161; }
            a { display: block; padding: 14px; border-radius: 12px; background: #E64A19; color: #FFFFFF;
                font-weight: 600; text-decoration: none; }
            a:focus-visible { outline: 3px solid #FF8A65; outline-offset: 2px; }
            small { display: block; margin-top: 16px; font-size: 12px; line-height: 18px; color: #9E9E9E; }
            </style>
            </head>
            <body>
            <main>
            <div class="mark">E</div>
            <h1>Đang mở Elsa Store…</h1>
            <p>Nếu ứng dụng không tự mở, hãy nhấn nút bên dưới. Tài khoản của bạn vẫn được lưu đăng nhập trong ứng dụng.</p>
            <a id="open" href="%s">Mở ứng dụng</a>
            <small>Mở liên kết này trên điện thoại đã cài Elsa Store.</small>
            </main>
            <script>
            var button = document.getElementById("open");
            var link = "%s";
            if (/android/i.test(navigator.userAgent)) { link = "%s"; button.href = link; }
            setTimeout(function () { window.location.href = link; }, 300);
            </script>
            </body>
            </html>
            """;

    @GetMapping("/{target}")
    public ResponseEntity<String> open(@PathVariable String target) {
        if (!TARGETS.contains(target)) {
            return ResponseEntity.notFound().build();
        }
        return page(target);
    }

    /** Liên kết chia sẻ sản phẩm / cửa hàng: {@code /app-links/products/12} mở {@code elsastore://products/12}. */
    @GetMapping("/{target}/{id:\\d+}")
    public ResponseEntity<String> openItem(@PathVariable String target, @PathVariable Long id) {
        if (!ITEM_TARGETS.contains(target)) {
            return ResponseEntity.notFound().build();
        }
        return page(target + "/" + id);
    }

    private static ResponseEntity<String> page(String target) {
        String deepLink = AppLinks.deepLink(target);
        // Chrome trên Android chỉ theo custom scheme ổn định qua URL intent://.
        String androidIntent = "intent://" + target + "#Intent;scheme=" + AppLinks.SCHEME + ";end";
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(PAGE.formatted(deepLink, deepLink, androidIntent));
    }
}
