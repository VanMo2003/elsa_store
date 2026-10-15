package com.example.elsa_store.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentVnPayResponse {
    String code;
    String message;
    String paymentUrl;
    Long orderId;
    Long paymentId;
    /** {@code vnp_TxnRef} của lần thanh toán này. */
    String transactionCode;
}
