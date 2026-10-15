package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.PaymentRequest;
import com.example.elsa_store.dto.response.PaymentResponse;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import com.example.elsa_store.dto.response.VnPayIpnResponse;
import com.example.elsa_store.dto.response.VnPayResultResponse;

import java.util.List;
import java.util.Map;

public interface PaymentService {
    PaymentResponse create(PaymentRequest req);

    PaymentResponse update(Long id, PaymentRequest req);

    void delete(Long id);

    PaymentResponse getById(Long id);

    List<PaymentResponse> getAll();

    /**
     * Tạo link thanh toán VNPay cho một đơn (người mua hoặc nhân sự cửa hàng). Mỗi lần gọi tạo một bản ghi thanh toán
     * với mã giao dịch riêng, nên trả lại sau khi thất bại hay link hết hạn đều được.
     */
    PaymentVnPayResponse createVnPayPayment(Long orderId, String bankCode, String clientIp);

    /**
     * Trình duyệt / app quay lại từ VNPay. Tham số chỉ được tin sau khi kiểm chữ ký; xử lý một lần duy nhất (gọi lại
     * nhiều lần chỉ đọc trạng thái).
     */
    VnPayResultResponse handleVnPayReturn(Map<String, String> params);

    /** VNPay gọi máy chủ → máy chủ (IPN); luôn trả mã theo định dạng VNPay, không ném lỗi. */
    VnPayIpnResponse handleVnPayIpn(Map<String, String> params);
}
