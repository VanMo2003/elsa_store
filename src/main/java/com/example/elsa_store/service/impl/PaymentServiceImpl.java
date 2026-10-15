package com.example.elsa_store.service.impl;

import com.example.elsa_store.config.VNPayConfig;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.PaymentRequest;
import com.example.elsa_store.dto.response.PaymentResponse;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import com.example.elsa_store.dto.response.VnPayIpnResponse;
import com.example.elsa_store.dto.response.VnPayResultResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Payment;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.PaymentMapper;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.PaymentRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.OrderNotificationService;
import com.example.elsa_store.service.PaymentService;
import com.example.elsa_store.utils.VNPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    static final String METHOD_VNPAY = "VNPAY";

    /** VNPay không nhận giao dịch dưới 5.000đ. */
    static final long MIN_VNPAY_AMOUNT = 5_000;

    private static final Pattern BANK_CODE = Pattern.compile("[A-Za-z0-9_]{2,20}");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final VNPayConfig vnPayConfig;
    private final OrderNotificationService orderNotificationService;
    private final AccessGuard accessGuard;
    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            VNPayConfig vnPayConfig,
            OrderNotificationService orderNotificationService,
            AccessGuard accessGuard,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.vnPayConfig = vnPayConfig;
        this.orderNotificationService = orderNotificationService;
        this.accessGuard = accessGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public PaymentResponse create(PaymentRequest req) {
        Order order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        accessGuard.authorize(order.getStore(), Access.STAFF_WRITE);
        Payment p = PaymentMapper.toEntity(req, order);
        p = paymentRepository.save(p);
        return PaymentMapper.toResponse(p);
    }

    @Override
    public PaymentResponse update(Long id, PaymentRequest req) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Order order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        accessGuard.authorize(p.getOrder().getStore(), Access.STAFF_WRITE);
        accessGuard.authorize(order.getStore(), Access.STAFF_WRITE);
        PaymentMapper.update(p, req, order);
        return PaymentMapper.toResponse(p);
    }

    @Override
    public void delete(Long id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessGuard.authorize(p.getOrder().getStore(), Access.MANAGER_WRITE);
        paymentRepository.delete(p);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessGuard.authorize(p.getOrder().getStore(), Access.READ);
        return PaymentMapper.toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAll() {
        User current = accessGuard.currentUser();
        return paymentRepository.findAll().stream()
                .filter(p -> current.getRole() == Role.ADMIN
                        || (p.getOrder().getStore() != null
                                && p.getOrder()
                                        .getStore()
                                        .getId()
                                        .equals(accessGuard.requireStoreMember().getId())))
                .map(PaymentMapper::toResponse)
                .toList();
    }

    @Override
    public PaymentVnPayResponse createVnPayPayment(Long orderId, String bankCode, String clientIp) {
        if (orderId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "orderId: must not be null");
        }
        // Số tiền luôn lấy từ đơn trong DB, không bao giờ từ app.
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        // Chỉ người đặt đơn hoặc người của cửa hàng bán mới tạo được link thanh toán.
        User current = accessGuard.currentUser();
        boolean buyer = order.getUser() != null && order.getUser().getId().equals(current.getId());
        if (!buyer) {
            accessGuard.authorize(order.getStore(), Access.STAFF_WRITE);
        }
        if (order.getStatus() == OrderStatus.DA_HUY) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order has been cancelled");
        }
        if (order.getPaymentStatus() == PaymentStatus.DA_THANH_TOAN) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order has already been paid");
        }
        long amountVnd = Math.round(order.getFinalAmount() == null ? 0 : order.getFinalAmount());
        if (amountVnd < MIN_VNPAY_AMOUNT) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order amount is too small for online payment");
        }
        String bank = bankCode == null || bankCode.isBlank() ? null : bankCode.trim();
        if (bank != null && !BANK_CODE.matcher(bank).matches()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "bankCode: invalid value");
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .paymentMethod(METHOD_VNPAY)
                .amount((double) amountVnd)
                .status(PaymentStatus.CHO_THANH_TOAN)
                .bankCode(bank)
                .build());
        // VNPay từ chối mã giao dịch trùng trong ngày — mỗi lần trả tiền một mã riêng: <đơn>-<lần thanh toán>.
        String txnRef = order.getId() + "-" + payment.getId();
        payment.setTransactionCode(txnRef);
        if (order.getPaymentStatus() != PaymentStatus.CHO_THANH_TOAN) {
            order.setPaymentStatus(PaymentStatus.CHO_THANH_TOAN);
        }

        Map<String, String> params = vnPayConfig.getVNPayConfig();
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
        params.put("vnp_TxnRef", txnRef);
        // VNPay chỉ nhận nội dung không dấu, không ký tự đặc biệt.
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getCode());
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        if (bank != null) {
            params.put("vnp_BankCode", bank);
        }
        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + VNPayUtil.query(params, true) + "&"
                + VNPayUtil.SECURE_HASH + "=" + VNPayUtil.sign(params, vnPayConfig.getSecretKey());

        return PaymentVnPayResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl(paymentUrl)
                .orderId(order.getId())
                .paymentId(payment.getId())
                .transactionCode(txnRef)
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnPayResultResponse handleVnPayReturn(Map<String, String> params) {
        if (!VNPayUtil.verify(params, vnPayConfig.getSecretKey())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid VNPay signature");
        }
        Outcome outcome = transactionTemplate.execute(status -> apply(params));
        return switch (outcome.kind()) {
            case NOT_FOUND -> throw new ResourceNotFoundException("Payment not found");
            case INVALID_AMOUNT -> throw new AppException(ErrorCode.BAD_REQUEST, "Invalid payment amount");
            case APPLIED, ALREADY_FINAL -> outcome.result();
        };
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnPayIpnResponse handleVnPayIpn(Map<String, String> params) {
        try {
            if (!VNPayUtil.verify(params, vnPayConfig.getSecretKey())) {
                log.warn("VNPay IPN with an invalid signature for TxnRef {}", params.get("vnp_TxnRef"));
                return VnPayIpnResponse.INVALID_SIGNATURE;
            }
            Outcome outcome = transactionTemplate.execute(status -> apply(params));
            return switch (outcome.kind()) {
                case NOT_FOUND -> VnPayIpnResponse.ORDER_NOT_FOUND;
                case INVALID_AMOUNT -> VnPayIpnResponse.INVALID_AMOUNT;
                case ALREADY_FINAL -> VnPayIpnResponse.ALREADY_CONFIRMED;
                case APPLIED -> VnPayIpnResponse.CONFIRMED;
            };
        } catch (RuntimeException e) {
            log.error("Cannot process VNPay IPN for TxnRef {}", params.get("vnp_TxnRef"), e);
            return VnPayIpnResponse.UNKNOWN_ERROR;
        }
    }

    enum OutcomeKind {
        APPLIED,
        ALREADY_FINAL,
        NOT_FOUND,
        INVALID_AMOUNT
    }

    record Outcome(OutcomeKind kind, VnPayResultResponse result) {}

    /** Ghi kết quả đã được ký vào thanh toán và đơn — trong một transaction, giữ khoá dòng thanh toán. */
    private Outcome apply(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        Payment payment = txnRef == null
                ? null
                : paymentRepository.findByTransactionCodeForUpdate(txnRef).orElse(null);
        if (payment == null) {
            return new Outcome(OutcomeKind.NOT_FOUND, null);
        }
        long expected = Math.round(payment.getAmount() == null ? 0 : payment.getAmount()) * 100;
        long received;
        try {
            received = Long.parseLong(params.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException e) {
            received = -1;
        }
        if (received != expected) {
            log.warn("VNPay amount mismatch for {}: expected {}, got {}", txnRef, expected, received);
            return new Outcome(OutcomeKind.INVALID_AMOUNT, null);
        }

        Order order = payment.getOrder();
        String responseCode = params.get("vnp_ResponseCode");
        if (payment.getStatus() != PaymentStatus.CHO_THANH_TOAN) {
            // Đã chốt bởi lần gọi trước (return URL hoặc IPN): chỉ báo lại trạng thái hiện tại.
            return new Outcome(OutcomeKind.ALREADY_FINAL, result(order, payment, payment.getResponseCode()));
        }

        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && (transactionStatus == null || "00".equals(transactionStatus));
        payment.setResponseCode(responseCode);
        payment.setGatewayTransactionNo(params.get("vnp_TransactionNo"));
        if (params.get("vnp_BankCode") != null) {
            payment.setBankCode(params.get("vnp_BankCode"));
        }

        PaymentStatus previous = order.getPaymentStatus();
        if (success) {
            payment.setStatus(PaymentStatus.DA_THANH_TOAN);
            payment.setPaidAt(LocalDateTime.now());
            order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
            // Trả trước thì cửa hàng không cần gọi xác nhận nữa.
            if (order.getStatus() == OrderStatus.CHUA_XAC_NHAN) {
                order.setStatus(OrderStatus.DA_XAC_NHAN);
            }
        } else {
            payment.setStatus(PaymentStatus.THAT_BAI);
            if (order.getPaymentStatus() != PaymentStatus.DA_THANH_TOAN) {
                order.setPaymentStatus(PaymentStatus.THAT_BAI);
            }
        }
        paymentRepository.save(payment);
        orderRepository.save(order);
        // Chỉ báo khi trạng thái thanh toán thật sự đổi, để VNPay gọi lại nhiều lần không gửi trùng thông báo / email.
        orderNotificationService.paymentChanged(order, previous);
        return new Outcome(OutcomeKind.APPLIED, result(order, payment, responseCode));
    }

    private static VnPayResultResponse result(Order order, Payment payment, String responseCode) {
        boolean paid = order.getPaymentStatus() == PaymentStatus.DA_THANH_TOAN;
        return VnPayResultResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .paid(paid)
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getStatus())
                .amount(payment.getAmount())
                .responseCode(responseCode)
                .message(paid ? "Thanh toán thành công" : failureMessage(responseCode))
                .build();
    }

    /** Lý do thất bại theo bảng mã của VNPay. */
    static String failureMessage(String responseCode) {
        if (responseCode == null) {
            return "Thanh toán không thành công";
        }
        return switch (responseCode) {
            case "24" -> "Bạn đã huỷ giao dịch";
            case "11" -> "Đã hết thời gian chờ thanh toán. Vui lòng thử lại";
            case "51" -> "Tài khoản không đủ số dư";
            case "65" -> "Tài khoản đã vượt hạn mức giao dịch trong ngày";
            case "12" -> "Thẻ hoặc tài khoản đang bị khoá";
            case "09" -> "Thẻ hoặc tài khoản chưa đăng ký Internet Banking";
            case "10", "13", "79" -> "Xác thực thẻ hoặc OTP không thành công";
            case "75" -> "Ngân hàng đang bảo trì. Vui lòng thử lại sau";
            case "07" -> "Giao dịch bị nghi ngờ, cửa hàng sẽ liên hệ để xác nhận";
            default -> "Thanh toán không thành công";
        };
    }
}
