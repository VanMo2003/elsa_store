package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {

    Optional<ReviewVote> findByReview_IdAndUser_Id(Long reviewId, Long userId);

    long countByReview_Id(Long reviewId);

    /** Trong các đánh giá {@code reviewIds}, những đánh giá {@code userId} đã bấm hữu ích. */
    @Query("select v.review.id from ReviewVote v where v.user.id = :userId and v.review.id in :reviewIds")
    List<Long> votedReviewIds(@Param("userId") Long userId, @Param("reviewIds") Collection<Long> reviewIds);

    @Modifying
    @Query("delete from ReviewVote v where v.review.id = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);
}
