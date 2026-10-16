package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Câu hỏi của khách về một sản phẩm. Người của cửa hàng trả lời (sửa lại được); câu đã trả lời ai cũng đọc được, câu
 * chưa trả lời chỉ người hỏi và cửa hàng thấy.
 */
@Entity
@Table(
        name = "product_questions",
        indexes = {
            @Index(name = "idx_question_product", columnList = "product_id, id"),
            @Index(name = "idx_question_user_product", columnList = "user_id, product_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductQuestion extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Khách đặt câu hỏi. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "question", length = 500, nullable = false)
    private String question;

    @Column(name = "answer", length = 1000)
    private String answer;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @ManyToOne
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    /** Số câu chưa được trả lời một khách được treo cho mỗi sản phẩm (chống spam). */
    public static final int MAX_PENDING_PER_PRODUCT = 3;
}
