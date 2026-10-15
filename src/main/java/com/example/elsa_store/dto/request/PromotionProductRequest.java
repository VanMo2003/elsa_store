package com.example.elsa_store.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Gắn một sản phẩm vào khuyến mãi (cùng cửa hàng). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromotionProductRequest {
    @NotNull
    Long promotionId;

    @NotNull
    Long productId;
}
