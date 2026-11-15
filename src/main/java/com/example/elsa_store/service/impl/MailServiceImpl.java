package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.event.OrderMailEvent;
import com.example.elsa_store.event.PasswordResetRequestedEvent;
import com.example.elsa_store.event.StoreReviewedEvent;
import com.example.elsa_store.service.MailService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/** Mail SMTP (biến MAIL_*). Không có MAIL_USERNAME thì chỉ ghi log, nên chạy local không cần máy chủ mail. */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private static final String BRAND = "Elsa Store";

    private static final String TEMPLATE =
            """
            <!doctype html>
            <html lang="vi">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            </head>
            <body style="margin:0;padding:0;background:#F5F5F5;font-family:Arial,Helvetica,sans-serif;color:#212121">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F5F5F5;padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="width:100%%;max-width:560px;background:#FFFFFF;border-radius:16px;overflow:hidden">
                    <tr><td style="background:#E64A19;padding:24px 32px;color:#FFFFFF">
                    <div style="font-size:12px;letter-spacing:1px;text-transform:uppercase;color:#FFD9CC">Elsa Store &middot; Fashion</div>
                    <div style="font-size:22px;line-height:30px;font-weight:bold;margin-top:6px">%s</div>
                    </td></tr>
                    <tr><td style="padding:28px 32px 8px;font-size:15px;line-height:23px">
                    <p style="margin:0 0 12px">Chào %s,</p>
                    <p style="margin:0">%s</p>
                    </td></tr>
                    %s
                    <tr><td style="padding:20px 32px 8px">
                    <a href="%s" style="display:inline-block;background:#E64A19;color:#FFFFFF;text-decoration:none;font-weight:bold;font-size:15px;padding:13px 24px;border-radius:10px">Mở ứng dụng Elsa Store</a>
                    </td></tr>
                    <tr><td style="padding:12px 32px 28px;font-size:13px;line-height:20px;color:#616161">
                    Hãy mở email này trên điện thoại đã cài Elsa Store. Tài khoản của bạn vẫn được lưu đăng nhập trong ứng dụng nên không cần đăng nhập lại.<br>
                    <span style="color:#9E9E9E">Nếu nút không hoạt động, mở liên kết: %s</span>
                    </td></tr>
                </table>
                </td></tr>
            </table>
            </body>
            </html>
            """;

    private static final String ORDER_TEMPLATE =
            """
            <tr><td style="padding:16px 32px 0">
            <div style="background:#FFF3EE;border-radius:10px;padding:14px 16px;font-size:14px;line-height:21px;color:#212121">
                <div style="font-weight:bold;color:#E64A19;margin-bottom:6px">Đơn hàng %s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px">
                %s
                <tr>
                    <td style="padding-top:8px;border-top:1px solid #F3D3C7;font-weight:bold">Tổng thanh toán</td>
                    <td align="right" style="padding-top:8px;border-top:1px solid #F3D3C7;font-weight:bold">%s</td>
                </tr>
                </table>
            </div>
            </td></tr>
            """;

    private static final String ITEM_TEMPLATE =
            """
            <tr>
                <td style="padding:3px 0">%s &times; %d</td>
                <td align="right" style="padding:3px 0">%s</td>
            </tr>
            """;

    /** Nội dung riêng của từng loại sự kiện: tiêu đề mail, tiêu đề lớn và đoạn nội dung (văn bản thường). */
    private record Copy(String subject, String headline, String message) {}

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String from;
    private final String publicUrl;

    public MailServiceImpl(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${app.mail.from:}") String from,
            @Value("${app.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.username = username;
        this.from = from;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Override
    public void sendOrderMail(OrderMailEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            return;
        }
        Copy copy = copy(event);
        if (copy == null) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username.isBlank()) {
            log.info("Mail is not configured (MAIL_USERNAME) - skipped \"{}\" to {}", copy.subject(), event.email());
            return;
        }

        String link = publicUrl + "/app-links/" + AppLinks.ORDERS;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from.isBlank() ? username : from, BRAND, "UTF-8"));
            helper.setTo(event.email());
            helper.setSubject(copy.subject());
            helper.setText(plainText(event, copy, link), html(event, copy, link));
            sender.send(message);
            log.info("Sent \"{}\" to {}", copy.subject(), event.email());
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.error("Cannot send \"{}\" to {}", copy.subject(), event.email(), e);
        }
    }

    private static final String REASON_TEMPLATE =
            """
            <tr><td style="padding:16px 32px 0">
            <div style="background:#FBE4E2;border-radius:10px;padding:14px 16px;font-size:14px;line-height:21px;color:#212121">
                <div style="font-weight:bold;color:#C62828;margin-bottom:4px">Lý do từ chối</div>
                %s
            </div>
            </td></tr>
            """;

    @Override
    public void sendStoreReview(StoreReviewedEvent review) {
        if (review.email() == null || review.email().isBlank()) {
            return;
        }
        String subject = review.approved()
                ? "[Elsa Store] Cửa hàng của bạn đã được duyệt"
                : "[Elsa Store] Hồ sơ cửa hàng chưa được duyệt";
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username.isBlank()) {
            log.info("Mail is not configured (MAIL_USERNAME) - skipped \"{}\" to {}", subject, review.email());
            return;
        }

        String link = publicUrl + "/app-links/" + AppLinks.STORE_STATUS;
        String name = review.ownerName() == null || review.ownerName().isBlank()
                ? review.ownerUsername()
                : review.ownerName();
        String headline = review.approved() ? "Cửa hàng đã được duyệt" : "Hồ sơ chưa được duyệt";
        String message = review.approved()
                ? "Cửa hàng " + review.storeName() + " đã được duyệt. Đăng nhập bằng tài khoản "
                        + review.ownerUsername() + " để thêm sản phẩm, quản lý và nhân viên."
                : "Hồ sơ cửa hàng " + review.storeName()
                        + " chưa được duyệt. Bạn có thể cập nhật thông tin và gửi lại ngay trong ứng dụng.";
        String reason = review.approved() ? "" : REASON_TEMPLATE.formatted(escape(review.reason()));
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(from.isBlank() ? username : from, BRAND, "UTF-8"));
            helper.setTo(review.email());
            helper.setSubject(subject);
            String plain = "Chào " + name + ",\n\n" + message
                    + (review.approved() ? "" : "\nLý do: " + review.reason()) + "\n\nMở ứng dụng: " + link + "\n";
            String html = TEMPLATE.formatted(escape(headline), escape(name), escape(message), reason, link, link);
            helper.setText(plain, html);
            sender.send(mime);
            log.info("Sent \"{}\" to {}", subject, review.email());
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.error("Cannot send \"{}\" to {}", subject, review.email(), e);
        }
    }

    private static final String RESET_TEMPLATE =
            """
            <!doctype html>
            <html lang="vi">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#F5F5F5;font-family:Arial,Helvetica,sans-serif;color:#212121">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F5F5F5;padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="width:100%%;max-width:560px;background:#FFFFFF;border-radius:16px;overflow:hidden">
                    <tr><td style="background:#E64A19;padding:24px 32px;color:#FFFFFF">
                    <div style="font-size:12px;letter-spacing:1px;text-transform:uppercase;color:#FFD9CC">Elsa Store &middot; Fashion</div>
                    <div style="font-size:22px;line-height:30px;font-weight:bold;margin-top:6px">Mã khôi phục mật khẩu</div>
                    </td></tr>
                    <tr><td style="padding:28px 32px 8px;font-size:15px;line-height:23px">
                    <p style="margin:0 0 12px">Chào %s,</p>
                    <p style="margin:0">Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu cho tài khoản <b>%s</b>. Nhập mã dưới đây trong ứng dụng Elsa Store:</p>
                    </td></tr>
                    <tr><td align="center" style="padding:20px 32px">
                    <div style="display:inline-block;background:#FFF3EE;border-radius:12px;padding:16px 28px;font-size:34px;letter-spacing:10px;font-weight:bold;color:#E64A19">%s</div>
                    </td></tr>
                    <tr><td style="padding:0 32px 28px;font-size:13px;line-height:20px;color:#616161">
                    Mã có hiệu lực trong %d phút và chỉ dùng được một lần. Không chia sẻ mã cho bất kỳ ai, kể cả nhân viên Elsa Store.<br>
                    Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu của bạn vẫn giữ nguyên.
                    </td></tr>
                </table>
                </td></tr>
            </table>
            </body>
            </html>
            """;

    @Override
    public void sendPasswordReset(PasswordResetRequestedEvent event) {
        String subject = "[Elsa Store] Mã khôi phục mật khẩu: " + event.code();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username.isBlank()) {
            // Chỉ xảy ra khi phát triển (chưa có SMTP): ghi mã ra log để còn thử được luồng quên mật khẩu.
            log.warn("Mail is not configured (MAIL_USERNAME) - password reset code for {} is {} (dev only)",
                    event.username(), event.code());
            return;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(from.isBlank() ? username : from, BRAND, "UTF-8"));
            helper.setTo(event.email());
            helper.setSubject(subject);
            String plain = "Chào " + event.name() + ",\n\nMã khôi phục mật khẩu cho tài khoản " + event.username() + " là: "
                    + event.code() + "\nMã có hiệu lực trong " + event.validMinutes()
                    + " phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.\n";
            String html = RESET_TEMPLATE.formatted(
                    escape(event.name()), escape(event.username()), event.code(), event.validMinutes());
            helper.setText(plain, html);
            sender.send(mime);
            log.info("Sent password reset code to {}", event.email());
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.error("Cannot send password reset code to {}", event.email(), e);
        }
    }

    private static Copy copy(OrderMailEvent event) {
        String code = event.orderCode();
        NotificationType type = event.type();
        return switch (type) {
            case ORDER_PLACED ->
                new Copy(
                        "[Elsa Store] Đặt hàng thành công - " + code,
                        "Đặt hàng thành công",
                        "Cảm ơn bạn đã đặt hàng tại Elsa Store. Đơn " + code
                                + " đã được ghi nhận và đang chờ xác nhận.");
            case ORDER_CONFIRMED ->
                new Copy(
                        "[Elsa Store] Đơn hàng " + code + " đã được xác nhận",
                        "Đơn hàng đã được xác nhận",
                        "Đơn " + code + " đã được xác nhận và đang được chuẩn bị.");
            case ORDER_SHIPPING ->
                new Copy(
                        "[Elsa Store] Đơn hàng " + code + " đang được giao",
                        "Đơn hàng đang được giao",
                        "Đơn " + code + " đã được giao cho "
                                + (event.tracking() == null ? "đơn vị vận chuyển" : event.tracking())
                                + ". Bạn chú ý điện thoại để nhận hàng nhé.");
            case ORDER_CANCELLED ->
                new Copy(
                        "[Elsa Store] Đơn hàng " + code + " đã bị huỷ",
                        "Đơn hàng đã bị huỷ",
                        "Đơn " + code + " đã bị huỷ"
                                + (event.cancelReason() == null ? "" : " (lý do: " + event.cancelReason() + ")")
                                + ". Nếu cần hỗ trợ, hãy liên hệ Elsa Store.");
            case ORDER_COMPLETED ->
                new Copy(
                        "[Elsa Store] Đơn hàng " + code + " đã hoàn thành",
                        "Đơn hàng đã hoàn thành",
                        "Đơn " + code + " đã hoàn thành. Cảm ơn bạn đã mua sắm tại Elsa Store.");
            case PAYMENT_SUCCESS ->
                new Copy(
                        "[Elsa Store] Thanh toán thành công - " + code,
                        "Thanh toán thành công",
                        "Elsa Store đã nhận được thanh toán cho đơn " + code + ".");
            case PAYMENT_FAILED ->
                new Copy(
                        "[Elsa Store] Thanh toán chưa thành công - " + code,
                        "Thanh toán chưa thành công",
                        "Thanh toán cho đơn " + code + " chưa thành công. Bạn có thể mở ứng dụng để thử thanh toán lại.");
            // Thông báo nội bộ (đơn mới cho cửa hàng, hồ sơ cửa hàng cho ADMIN) chỉ hiện trong app, không gửi email đơn hàng.
            case NEW_ORDER, STORE_REGISTERED, STORE_APPROVED, STORE_REJECTED, LOW_STOCK, RETURN_REQUESTED,
                    RETURN_UPDATED, REVIEW_NEW, REVIEW_REPLIED, BACK_IN_STOCK, PRICE_DROP, NEW_PRODUCT, QUESTION_NEW,
                    QUESTION_ANSWERED, TIER_UP, CART_REMINDER, STORE_SUSPENDED,
                    STORE_REINSTATED, PRODUCT_REPORTED, REPORT_HANDLED, ORDER_DELIVERY_CHECK, REFERRAL_REWARD, STORE_OFFER -> null;
        };
    }

    private static String html(OrderMailEvent event, Copy copy, String link) {
        StringBuilder rows = new StringBuilder();
        for (OrderMailEvent.Line line : event.items()) {
            rows.append(ITEM_TEMPLATE.formatted(
                    escape(line.name()), line.quantity(), NotificationMessages.money(line.lineTotal())));
        }
        String order = ORDER_TEMPLATE.formatted(
                escape(event.orderCode()), rows, NotificationMessages.money(event.finalAmount()));
        return TEMPLATE.formatted(
                escape(copy.headline()), escape(event.customerName()), escape(copy.message()), order, link, link);
    }

    private static String plainText(OrderMailEvent event, Copy copy, String link) {
        StringBuilder text = new StringBuilder("Chào ")
                .append(event.customerName())
                .append(",\n\n")
                .append(copy.message())
                .append("\n\n");
        for (OrderMailEvent.Line line : event.items()) {
            text.append("- ")
                    .append(line.name())
                    .append(" x ")
                    .append(line.quantity())
                    .append(": ")
                    .append(NotificationMessages.money(line.lineTotal()))
                    .append('\n');
        }
        return text.append("Tổng thanh toán: ")
                .append(NotificationMessages.money(event.finalAmount()))
                .append("\n\nMở ứng dụng: ")
                .append(link)
                .append('\n')
                .toString();
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
