package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bản tóm tắt đánh giá do AI viết cho một sản phẩm ("Khách nói gì"), lưu lại để không gọi mô hình mỗi lượt xem. Chỉ viết
 * lại khi {@code signature} (số đánh giá, đánh giá mới nhất, lần sửa gần nhất) đổi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "review_digests", uniqueConstraints = @UniqueConstraint(name = "uk_review_digest_product", columnNames = "product_id"))
public class ReviewDigest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "signature", length = 100)
    private String signature;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Ưu điểm, mỗi dòng một ý. */
    @Column(name = "pros", columnDefinition = "TEXT")
    private String pros;

    /** Điểm cần lưu ý, mỗi dòng một ý. */
    @Column(name = "cons", columnDefinition = "TEXT")
    private String cons;

    /** Số đánh giá có nhận xét đã đọc. */
    @Column(name = "based_on")
    private Integer basedOn;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
