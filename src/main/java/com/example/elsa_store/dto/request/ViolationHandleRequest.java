package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.ViolationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/** ADMIN xử lý ({@code RESOLVED}) hoặc bỏ qua ({@code DISMISSED}) báo cáo vi phạm. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationHandleRequest {

    @NotNull
    private ViolationStatus status;

    @Size(max = 500)
    private String note;
}
