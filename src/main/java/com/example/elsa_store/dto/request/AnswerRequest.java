package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Cửa hàng trả lời câu hỏi của khách. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerRequest {

    @NotBlank
    @Size(max = 1000)
    private String answer;
}
