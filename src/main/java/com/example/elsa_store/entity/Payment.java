
package com.example.elsa_store.entity;

import com.example.elsa_store.constant.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Mã giao dịch phía cửa hàng gửi cho VNPay ({@code vnp_TxnRef}) — mỗi lần thanh toán một mã, không lặp. */
    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    /** Mã giao dịch phía VNPay ({@code vnp_TransactionNo}) để đối soát. */
    @Column(name = "gateway_transaction_no", length = 50)
    private String gatewayTransactionNo;

    /** Ngân hàng khách chọn ({@code vnp_BankCode}). */
    @Column(name = "bank_code", length = 30)
    private String bankCode;

    /** Mã kết quả VNPay ({@code vnp_ResponseCode}); "00" là thành công. */
    @Column(name = "response_code", length = 10)
    private String responseCode;
}
