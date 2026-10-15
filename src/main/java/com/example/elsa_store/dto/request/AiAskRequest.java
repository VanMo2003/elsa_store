package com.example.elsa_store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

/** Hỏi trợ lý mua sắm Elsa (không cần mở cuộc trò chuyện): app giữ lịch sử và gửi kèm vài lượt gần nhất. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAskRequest {

    @NotBlank
    @Size(max = 500)
    private String question;

    /** Các lượt trước đó, cũ nhất trước; chỉ lấy tối đa 6 lượt cuối. */
    @Valid
    @Size(max = 20)
    private List<Turn> history;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Turn {
        /** {@code user} hoặc {@code assistant}. */
        @NotBlank
        private String role;

        @NotBlank
        @Size(max = 2000)
        private String content;
    }
}
