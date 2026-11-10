package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.event.OrderMailEvent;
import com.example.elsa_store.event.StoreReviewedEvent;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceImplTest {

    private static final String PUBLIC_URL = "http://192.168.1.10:8080/elsa-store/api/";

    private static OrderMailEvent event(NotificationType type) {
        return event(type, null);
    }

    private static OrderMailEvent event(NotificationType type, String cancelReason) {
        return event(type, cancelReason, null);
    }

    private static OrderMailEvent event(NotificationType type, String cancelReason, String tracking) {
        return new OrderMailEvent(
                type,
                "khach@example.com",
                "Nguyễn Văn A",
                "ORD-1768188571731",
                1_250_000.0,
                List.of(new OrderMailEvent.Line("Giày Samba <OG> (Trắng, size 42)", 2, 1_250_000)),
                cancelReason,
                tracking);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private static String textOf(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        StringBuilder all = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                all.append(textOf(child)).append('\n');
            }
        }
        return all.toString();
    }

    @Test
    void placedMailHasSubjectItemsTotalAndOpenAppLink() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        service.sendOrderMail(event(NotificationType.ORDER_PLACED));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertEquals("[Elsa Store] Đặt hàng thành công - ORD-1768188571731", message.getSubject());

        String body = textOf(message);
        assertTrue(body.contains("ORD-1768188571731"));
        assertTrue(body.contains("1.250.000đ"), "tổng tiền định dạng kiểu Việt Nam");
        assertTrue(body.contains("Giày Samba &lt;OG&gt;"), "tên sản phẩm phải được escape HTML");
        assertTrue(
                body.contains("http://192.168.1.10:8080/elsa-store/api/app-links/orders"),
                "nút mở app trỏ về trang /app-links của BE");
    }

    @Test
    void everyCustomerFacingTypeRendersWithoutFormatErrors() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        List<NotificationType> customerFacing = List.of(
                NotificationType.ORDER_PLACED,
                NotificationType.ORDER_CONFIRMED,
                NotificationType.ORDER_SHIPPING,
                NotificationType.ORDER_CANCELLED,
                NotificationType.ORDER_COMPLETED,
                NotificationType.PAYMENT_SUCCESS,
                NotificationType.PAYMENT_FAILED);
        for (NotificationType type : customerFacing) {
            service.sendOrderMail(event(type));
        }

        verify(sender, org.mockito.Mockito.times(customerFacing.size())).send(any(MimeMessage.class));
    }

    @Test
    void cancellationMailTellsTheReason() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        service.sendOrderMail(event(NotificationType.ORDER_CANCELLED, "Hết size <42>"));
        service.sendOrderMail(event(NotificationType.ORDER_SHIPPING));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, org.mockito.Mockito.times(2)).send(sent.capture());
        String cancelled = textOf(sent.getAllValues().get(0));
        assertTrue(cancelled.contains("lý do: Hết size &lt;42&gt;"), "lý do huỷ phải có và được escape HTML");
        assertEquals("[Elsa Store] Đơn hàng ORD-1768188571731 đang được giao", sent.getAllValues().get(1).getSubject());
        assertTrue(textOf(sent.getAllValues().get(1)).contains("đơn vị vận chuyển"), "chưa có mã thì viết chung chung");
    }

    @Test
    void shippingMailNamesTheCarrierAndTrackingCode() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        service.sendOrderMail(event(NotificationType.ORDER_SHIPPING, null, "GHN, mã vận đơn GHN123"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        assertTrue(textOf(sent.getValue()).contains("đã được giao cho GHN, mã vận đơn GHN123"));
    }

    @Test
    void internalNotificationTypesAreNeverSentAsOrderMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        for (NotificationType type : List.of(
                NotificationType.STORE_REGISTERED, NotificationType.STORE_APPROVED, NotificationType.STORE_REJECTED)) {
            service.sendOrderMail(event(type));
        }

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void storeReviewMailCarriesTheDecisionAndTheRejectionReason() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        service.sendStoreReview(new StoreReviewedEvent(
                false, "chu@example.com", "Lê Văn Chủ", "chuchuchu", "Cửa hàng <Giày>", "Thiếu giấy phép kinh doanh"));
        service.sendStoreReview(new StoreReviewedEvent(
                true, "chu@example.com", "Lê Văn Chủ", "chuchuchu", "Cửa hàng Giày", null));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, org.mockito.Mockito.times(2)).send(sent.capture());
        MimeMessage rejected = sent.getAllValues().get(0);
        assertEquals("[Elsa Store] Hồ sơ cửa hàng chưa được duyệt", rejected.getSubject());
        String rejectedBody = textOf(rejected);
        assertTrue(rejectedBody.contains("Thiếu giấy phép kinh doanh"), "phải nêu lý do từ chối");
        assertTrue(rejectedBody.contains("Cửa hàng &lt;Giày&gt;"), "tên cửa hàng phải được escape HTML");
        assertTrue(rejectedBody.contains("/app-links/store-status"));

        MimeMessage approved = sent.getAllValues().get(1);
        assertEquals("[Elsa Store] Cửa hàng của bạn đã được duyệt", approved.getSubject());
        assertTrue(!textOf(approved).contains("Lý do từ chối"));
    }

    @Test
    void newOrderIsNeverEmailed() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailServiceImpl service = new MailServiceImpl(provider(sender), "shop@example.com", "", PUBLIC_URL);

        service.sendOrderMail(event(NotificationType.NEW_ORDER));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void withoutMailUsernameNothingIsSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailServiceImpl service = new MailServiceImpl(provider(sender), "", "", PUBLIC_URL);

        service.sendOrderMail(event(NotificationType.ORDER_PLACED));

        verify(sender, never()).send(any(MimeMessage.class));
    }
}
