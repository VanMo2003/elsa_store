
package com.example.elsa_store.service.impl;

import com.example.elsa_store.config.VNPayConfig;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.PaymentRequest;
import com.example.elsa_store.dto.response.PaymentResponse;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Payment;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.PaymentMapper;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.PaymentRepository;
import com.example.elsa_store.service.PaymentService;
import com.example.elsa_store.utils.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final VNPayConfig vnPayConfig;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              OrderRepository orderRepository, VNPayConfig vnPayConfig) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.vnPayConfig = vnPayConfig;
    }

    @Override
    public PaymentResponse create(PaymentRequest req) {
        Order order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
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
        PaymentMapper.update(p, req, order);
        return PaymentMapper.toResponse(p);
    }

    @Override
    public void delete(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        paymentRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return PaymentMapper.toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAll() {
        return paymentRepository.findAll().stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    @Override
    public PaymentVnPayResponse createVnPayPayment(HttpServletRequest request) {
        String orderIdStr = request.getParameter("orderId");

        if (orderIdStr == null || orderIdStr.isEmpty()) {
            throw new RuntimeException("orderId is required");
        }

        Long orderId = Long.parseLong(orderIdStr);

        // 🔥 Lấy order từ DB để tránh sửa tiền từ FE
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // VNPay yêu cầu nhân 100
        long amount = order.getFinalAmount().longValue() * 100L;

        String bankCode = request.getParameter("bankCode");

        Map<String, String> vnpParamsMap = vnPayConfig.getVNPayConfig();

        vnpParamsMap.put("vnp_Amount", String.valueOf(amount));

        // 🔥 BẮT BUỘC
        vnpParamsMap.put("vnp_TxnRef", String.valueOf(orderId));

        if (bankCode != null && !bankCode.isEmpty()) {
            vnpParamsMap.put("vnp_BankCode", bankCode);
        }

        vnpParamsMap.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));

        // build query url
        String queryUrl = VNPayUtil.getPaymentURL(vnpParamsMap, true);
        String hashData = VNPayUtil.getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = VNPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData);

        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + queryUrl;

        return PaymentVnPayResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl(paymentUrl)
                .build();
    }

    @Override
    public void handleVnPayCallback(HttpServletRequest request) {

        String responseCode = request.getParameter("vnp_ResponseCode");
        String txnRef = request.getParameter("vnp_TxnRef");
        // vnp_TxnRef nên lưu orderId khi tạo thanh toán

        if (txnRef == null) {
            throw new RuntimeException("Missing order reference");
        }

        Long orderId = Long.parseLong(txnRef);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

//        Payment payment = paymentRepository.findTopByOrder_IdOrderByIdDesc(orderId)
//                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if ("00".equals(responseCode)) {
            // Thanh toán thành công

//            payment.setStatus(PaymentStatus.DA_THANH_TOAN);
            order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);

            // Auto xác nhận đơn nếu online payment
            if (order.getStatus() == OrderStatus.CHUA_XAC_NHAN) {
                order.setStatus(OrderStatus.DA_XAC_NHAN);
            }

        } else {
//            payment.setStatus(PaymentStatus.THAT_BAI);
            order.setPaymentStatus(PaymentStatus.THAT_BAI);
        }

//        paymentRepository.save(payment);
        orderRepository.save(order);
    }
}
