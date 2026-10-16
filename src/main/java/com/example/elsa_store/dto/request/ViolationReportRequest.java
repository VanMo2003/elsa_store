package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.ViolationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Khách báo cáo sản phẩm vi phạm. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReportRequest {

    @NotNull
    private ViolationReason reason;

    @Size(max = 500)
    private String note;
}
