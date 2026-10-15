package com.example.elsa_store.service.impl;

import com.example.elsa_store.config.VNPayConfig;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import com.example.elsa_store.dto.response.VnPayIpnResponse;
import com.example.elsa_store.dto.response.VnPayResultResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Payment;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.PaymentRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.OrderNotificationService;
import com.example.elsa_store.utils.VNPayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    private static final String SECRET = "TESTSECRETKEY1234567890ABCDEFGHI";

    /** Transaction giả: test chỉ kiểm logic, không cần DB. */
    static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderNotificationService notifications = mock(OrderNotificationService.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final VNPayConfig config = new VNPayConfig();
    private PaymentServiceImpl service;

    private User buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(config, "vnp_PayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(config, "vnp_ReturnUrl", "http://localhost:8080/elsa-store/api/payment/vn-pay-callback");
        ReflectionTestUtils.setField(config, "vnp_TmnCode", "TESTTMN1");
        ReflectionTestUtils.setField(config, "secretKey", SECRET);
        ReflectionTestUtils.setField(config, "vnp_Version", "2.1.0");
        ReflectionTestUtils.setField(config, "vnp_Command", "pay");
        ReflectionTestUtils.setField(config, "orderType", "other");
        service = new PaymentServiceImpl(
                payments, orders, config, notifications, accessGuard, new NoopTransactionManager(),
                mock(OrderTimeline.class));

        buyer = User.builder().username("khach001").build();
        buyer.setId(7L);
        Store store = Store.builder().name("Store A").build();
        store.setId(3L);
        order = Order.builder()
                .code("ORD-1")
                .user(buyer)
                .store(store)
                .status(OrderStatus.CHUA_XAC_NHAN)
                .paymentStatus(PaymentStatus.CHUA_THANH_TOAN)
                .finalAmount(1_250_000.0)
                .build();
        order.setId(42L);
        when(accessGuard.currentUser()).thenReturn(buyer);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(payments.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L + saved.size());
                saved.add(p);
            }
            return p;
        });
        // Mã giao dịch được gán sau lần save đầu (JPA tự lưu khi commit) — tra theo giá trị hiện tại của bản ghi.
        when(payments.findByTransactionCodeForUpdate(any())).thenAnswer(inv -> saved.stream()
                .filter(p -> inv.getArgument(0).equals(p.getTransactionCode()))
                .findFirst());
    }

    private final List<Payment> saved = new ArrayList<>();

    private Payment payment(String txnRef) {
        return saved.stream()
                .filter(p -> txnRef.equals(p.getTransactionCode()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, String> createLinkParams() {
        PaymentVnPayResponse link = service.createVnPayPayment(42L, "NCB", "10.0.0.5");
        Map<String, String> params = new LinkedHashMap<>();
        String query = URI.create(link.getPaymentUrl()).getRawQuery();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.US_ASCII));
        }
        return params;
    }

    /** Tham số VNPay trả về, ký bằng khoá bí mật như VNPay thật. */
    private static Map<String, String> vnpayReturn(String txnRef, long amount, String responseCode) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TESTTMN1");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Thanh toan don hang ORD-1");
        params.put("vnp_PayDate", "20261014101500");
        params.put(VNPayUtil.SECURE_HASH, VNPayUtil.sign(params, SECRET));
        return params;
    }

    @Test
    void paymentLinkIsSignedUsesTheOrderAmountAndAFreshTransactionRef() {
        Map<String, String> params = createLinkParams();

        assertEquals("125000000", params.get("vnp_Amount"));
        assertEquals("42-100", params.get("vnp_TxnRef"));
        assertEquals("Thanh toan don hang ORD-1", params.get("vnp_OrderInfo"));
        assertEquals("NCB", params.get("vnp_BankCode"));
        assertTrue(VNPayUtil.verify(params, SECRET), "chữ ký trên link phải khớp");
        assertEquals(PaymentStatus.CHO_THANH_TOAN, order.getPaymentStatus());
        assertNotNull(params.get("vnp_CreateDate"));
    }

    @Test
    void cannotPayCancelledOrAlreadyPaidOrders() {
        order.setStatus(OrderStatus.DA_HUY);
        AppException cancelled = assertThrows(AppException.class, () -> service.createVnPayPayment(42L, null, "1.1.1.1"));
        assertEquals("Order has been cancelled", cancelled.getMessage());

        order.setStatus(OrderStatus.DA_XAC_NHAN);
        order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
        AppException paid = assertThrows(AppException.class, () -> service.createVnPayPayment(42L, null, "1.1.1.1"));
        assertEquals("Order has already been paid", paid.getMessage());
    }

    @Test
    void someoneElseCannotCreateALinkForTheOrder() {
        User stranger = User.builder().username("khach002").build();
        stranger.setId(8L);
        when(accessGuard.currentUser()).thenReturn(stranger);
        doThrow(new AppException(ErrorCode.UN_AUTHORIZED))
                .when(accessGuard)
                .authorize(eq(order.getStore()), any());

        assertThrows(AppException.class, () -> service.createVnPayPayment(42L, null, "1.1.1.1"));
        verify(payments, never()).save(any());
    }

    @Test
    void forgedCallbackIsRejectedAndChangesNothing() {
        String txnRef = createLinkParams().get("vnp_TxnRef");
        Map<String, String> forged = vnpayReturn(txnRef, 125_000_000L, "00");
        forged.put(VNPayUtil.SECURE_HASH, "deadbeef");

        AppException ex = assertThrows(AppException.class, () -> service.handleVnPayReturn(forged));
        assertEquals("Invalid VNPay signature", ex.getMessage());
        assertEquals(PaymentStatus.CHO_THANH_TOAN, order.getPaymentStatus());

        // Kẻ gian đổi mã kết quả sau khi VNPay đã ký (thất bại → thành công) cũng không qua được.
        Map<String, String> tampered = vnpayReturn(txnRef, 125_000_000L, "24");
        tampered.put("vnp_ResponseCode", "00");
        assertThrows(AppException.class, () -> service.handleVnPayReturn(tampered));
        verify(notifications, never()).paymentChanged(any(), any());
    }

    @Test
    void successfulPaymentIsAppliedExactlyOnce() {
        String txnRef = createLinkParams().get("vnp_TxnRef");
        Map<String, String> result = vnpayReturn(txnRef, 125_000_000L, "00");

        VnPayResultResponse first = service.handleVnPayReturn(result);
        assertTrue(first.isPaid());
        assertEquals("Thanh toán thành công", first.getMessage());
        assertEquals(PaymentStatus.DA_THANH_TOAN, order.getPaymentStatus());
        assertEquals(OrderStatus.DA_XAC_NHAN, order.getStatus());
        Payment payment = payment(txnRef);
        assertEquals(PaymentStatus.DA_THANH_TOAN, payment.getStatus());
        assertEquals("14123456", payment.getGatewayTransactionNo());
        assertNotNull(payment.getPaidAt());

        // IPN tới sau với cùng kết quả: báo đã xác nhận, không gửi thông báo lần hai.
        assertEquals(VnPayIpnResponse.ALREADY_CONFIRMED, service.handleVnPayIpn(result));
        VnPayResultResponse again = service.handleVnPayReturn(result);
        assertTrue(again.isPaid());
        verify(notifications, times(1)).paymentChanged(eq(order), eq(PaymentStatus.CHO_THANH_TOAN));
    }

    @Test
    void cancelledAtVnPayMarksThePaymentFailedWithAReadableReason() {
        String txnRef = createLinkParams().get("vnp_TxnRef");
        VnPayResultResponse result = service.handleVnPayReturn(vnpayReturn(txnRef, 125_000_000L, "24"));

        assertFalse(result.isPaid());
        assertEquals("Bạn đã huỷ giao dịch", result.getMessage());
        assertEquals(PaymentStatus.THAT_BAI, order.getPaymentStatus());
        assertEquals(OrderStatus.CHUA_XAC_NHAN, order.getStatus());
    }

    @Test
    void amountMismatchIsRejected() {
        String txnRef = createLinkParams().get("vnp_TxnRef");
        Map<String, String> cheap = vnpayReturn(txnRef, 100L, "00");

        assertEquals(VnPayIpnResponse.INVALID_AMOUNT, service.handleVnPayIpn(cheap));
        assertThrows(AppException.class, () -> service.handleVnPayReturn(cheap));
        assertEquals(PaymentStatus.CHO_THANH_TOAN, order.getPaymentStatus());
    }

    @Test
    void ipnAnswersInVnPayFormat() {
        String txnRef = createLinkParams().get("vnp_TxnRef");
        assertEquals(VnPayIpnResponse.ORDER_NOT_FOUND, service.handleVnPayIpn(vnpayReturn("999-999", 125_000_000L, "00")));
        Map<String, String> bad = vnpayReturn(txnRef, 125_000_000L, "00");
        bad.put(VNPayUtil.SECURE_HASH, "00");
        assertEquals(VnPayIpnResponse.INVALID_SIGNATURE, service.handleVnPayIpn(bad));
        assertEquals(VnPayIpnResponse.CONFIRMED, service.handleVnPayIpn(vnpayReturn(txnRef, 125_000_000L, "00")));
        assertEquals("00", VnPayIpnResponse.CONFIRMED.rspCode());
    }

    @Test
    void tinyOrdersCannotUseVnPay() {
        order.setFinalAmount(1_000.0);
        AppException ex = assertThrows(AppException.class, () -> service.createVnPayPayment(42L, null, "1.1.1.1"));
        assertEquals("Order amount is too small for online payment", ex.getMessage());
    }
}
