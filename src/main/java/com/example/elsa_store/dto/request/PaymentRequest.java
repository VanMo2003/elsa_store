
package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class PaymentRequest {

    @NotNull
    private Long orderId;

    private String paymentMethod;
    private Double amount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private String transactionCode;

    public Long getOrderId() { return orderId; }

    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPaymentMethod() { return paymentMethod; }

    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public Double getAmount() { return amount; }

    public void setAmount(Double amount) { this.amount = amount; }

    public PaymentStatus getStatus() { return status; }

    public void setStatus(PaymentStatus status) { this.status = status; }

    public LocalDateTime getPaidAt() { return paidAt; }

    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getTransactionCode() { return transactionCode; }

    public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
}
