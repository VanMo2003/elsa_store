package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

/** Khách bấm "Hữu ích" cho một đánh giá (mỗi khách một lần cho mỗi đánh giá). */
@Entity
@Table(
        name = "review_helpful_votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_review_user", columnNames = {"review_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewVote extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview review;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
