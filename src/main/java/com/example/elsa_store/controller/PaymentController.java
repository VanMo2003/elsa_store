package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.PaymentRequest;
import com.example.elsa_store.dto.response.PaymentResponse;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import com.example.elsa_store.dto.response.VnPayIpnResponse;
import com.example.elsa_store.dto.response.VnPayResultResponse;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.service.PaymentService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody PaymentRequest req) {
        return ApiResponse.ok(paymentService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaymentResponse> update(@PathVariable Long id, @Valid @RequestBody PaymentRequest req) {
        return ApiResponse.ok(paymentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PaymentResponse>> getAll() {
        return ApiResponse.ok(paymentService.getAll());
    }

    /** Link thanh toán VNPay cho một đơn; số tiền lấy từ đơn trong DB. */
    @GetMapping("/vn-pay")
    public ApiResponse<PaymentVnPayResponse> pay(
            @RequestParam Long orderId,
            @RequestParam(required = false) String bankCode,
            HttpServletRequest request) {
        return ApiResponse.ok(
                paymentService.createVnPayPayment(orderId, bankCode, VNPayUtil.getIpAddress(request)));
    }

    /**
     * Địa chỉ VNPay chuyển người mua về sau khi trả tiền ({@code vnpay.returnUrl}). Công khai: chữ ký VNPay trong tham số
     * chính là bằng chứng, không ai sửa được kết quả. App gọi bằng JSON để đọc kết quả; trình duyệt (Accept: text/html)
     * nhận một trang kết quả kèm nút mở app.
     */
    @GetMapping("/vn-pay-callback")
    public ResponseEntity<?> payCallbackHandler(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        boolean html = accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
        if (!html) {
            return ResponseEntity.ok(ApiResponse.ok(paymentService.handleVnPayReturn(params)));
        }
        String title;
        String message;
        boolean paid = false;
        try {
            VnPayResultResponse result = paymentService.handleVnPayReturn(params);
            paid = result.isPaid();
            title = paid ? "Thanh toán thành công" : "Thanh toán chưa thành công";
            message = paid
                    ? "Đơn " + result.getOrderCode() + " đã được thanh toán. Cảm ơn bạn đã mua sắm tại Elsa Store."
                    : result.getMessage() + ". Bạn có thể thanh toán lại trong mục Đơn hàng.";
        } catch (AppException e) {
            title = "Không xác nhận được giao dịch";
            message = "Liên kết thanh toán không hợp lệ hoặc đã bị chỉnh sửa.";
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(resultPage(title, message, paid));
    }

    /** VNPay gọi máy chủ → máy chủ để báo kết quả (cần BE có địa chỉ công khai). Công khai, xác thực bằng chữ ký. */
    @GetMapping("/vnpay-ipn")
    public VnPayIpnResponse ipn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnPayIpn(params);
    }

    private static String resultPage(String title, String message, boolean paid) {
        String deepLink = AppLinks.orders();
        String androidIntent = "intent://" + AppLinks.ORDERS + "#Intent;scheme=" + AppLinks.SCHEME + ";end";
        return """
                <!doctype html>
                <html lang="vi">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
                        background: #F5F5F5; color: #212121;
                        font-family: system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif; }
                main { background: #FFFFFF; border-radius: 20px; padding: 32px 28px; margin: 24px; max-width: 360px;
                        text-align: center; box-shadow: 0 8px 24px rgba(33, 33, 33, .08); }
                .mark { width: 64px; height: 64px; margin: 0 auto 18px; border-radius: 32px; color: #FFFFFF;
                        font-size: 30px; font-weight: 700; line-height: 64px; background: %s; }
                h1 { margin: 0 0 8px; font-size: 20px; }
                p { margin: 0 0 22px; font-size: 14px; line-height: 21px; color: #616161; }
                a { display: block; padding: 14px; border-radius: 12px; background: #E64A19; color: #FFFFFF;
                    font-weight: 600; text-decoration: none; }
                </style>
                </head>
                <body>
                <main>
                <div class="mark">%s</div>
                <h1>%s</h1>
                <p>%s</p>
                <a id="open" href="%s">Mở ứng dụng Elsa Store</a>
                </main>
                <script>
                if (/android/i.test(navigator.userAgent)) { document.getElementById("open").href = "%s"; }
                </script>
                </body>
                </html>
                """
                .formatted(
                        HtmlUtils.htmlEscape(title, "UTF-8"),
                        paid ? "#2E7D32" : "#C62828",
                        paid ? "&#10003;" : "!",
                        HtmlUtils.htmlEscape(title, "UTF-8"),
                        HtmlUtils.htmlEscape(message, "UTF-8"),
                        deepLink,
                        androidIntent);
    }
}
