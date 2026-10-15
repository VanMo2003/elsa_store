package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Cửa hàng xử lý yêu cầu trả hàng: {@code note} là lời nhắn cho khách (bắt buộc khi từ chối); {@code refundAmount} chỉ
 * dùng khi hoàn tiền (trống = số tiền gợi ý).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnDecisionRequest {
    @Size(max = 500)
    String note;

    @DecimalMin("0")
    Double refundAmount;
}
