package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Kết quả một lần thanh toán VNPay sau khi BE đã kiểm chữ ký và cập nhật đơn. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VnPayResultResponse {
    Long orderId;
    String orderCode;
    /** Đơn đã được thanh toán (bởi lần này hoặc một lần trước đó). */
    boolean paid;
    PaymentStatus paymentStatus;
    OrderStatus orderStatus;
    Double amount;
    /** Mã kết quả VNPay ({@code vnp_ResponseCode}). */
    String responseCode;
    /** Câu tiếng Việt cho người mua. */
    String message;
}
