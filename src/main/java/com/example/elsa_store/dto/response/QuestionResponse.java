package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private String question;
    /** Tên đăng nhập của người hỏi, đã che bớt. */
    private String askerName;
    private String askerAvatarUrl;
    private LocalDateTime createdAt;
    /** null = cửa hàng chưa trả lời. */
    private String answer;
    private LocalDateTime answeredAt;
    /** Tên cửa hàng trả lời — hiện "Phản hồi từ ...". */
    private String storeName;
    /** Câu hỏi của chính người đang xem. */
    private Boolean mine;
}
