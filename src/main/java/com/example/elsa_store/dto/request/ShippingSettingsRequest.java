package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Phí giao hàng của cửa hàng. {@code shippingFee} trống hoặc 0 = giao miễn phí; {@code freeShippingThreshold} trống = không
 * có mức miễn phí (gửi trống để bỏ mức đang có).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShippingSettingsRequest {
    @DecimalMin("0")
    @DecimalMax("1000000")
    Double shippingFee;

    @DecimalMin("0")
    @DecimalMax("1000000000")
    Double freeShippingThreshold;
}
