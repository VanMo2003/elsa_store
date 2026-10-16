package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickReplyRequest {

    @NotBlank
    @Size(max = 60)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String content;
}
