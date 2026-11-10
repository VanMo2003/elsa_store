package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Đơn vị vận chuyển và mã vận đơn của đơn đang giao. Gửi cả hai trống để xoá. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackingRequest {
    @Size(max = 50)
    String carrier;

    @Size(max = 50)
    @Pattern(regexp = "^\\s*[A-Za-z0-9._/-]*\\s*$", message = "trackingCode: chỉ gồm chữ không dấu, số và - _ . /")
    String trackingCode;
}
