package com.example.elsa_store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/** Khách xin trả một số món của đơn đã hoàn thành. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnCreateRequest {
    @NotNull
    Long orderId;

    @NotEmpty
    @Valid
    List<Line> items;

    @NotBlank
    @Size(max = 255)
    String reason;

    @Size(max = 1000)
    String note;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Line {
        @NotNull
        Long productVariantId;

        @NotNull
        @Min(1)
        Integer quantity;
    }
}
