package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    /** Sản phẩm của cửa hàng đính kèm tin (tối đa 3): khách hỏi về sản phẩm, nhân viên chia sẻ sản phẩm. */
    @Size(max = 3)
    private List<Long> productIds;

    /**
     * Khách muốn trợ lý AI trả lời tin này ngay (gợi ý sản phẩm phù hợp…) thay vì chờ nhân viên. Bị bỏ qua với
     * tin của cửa hàng.
     */
    private Boolean askAi;
}
