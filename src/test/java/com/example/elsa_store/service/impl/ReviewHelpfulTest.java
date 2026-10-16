package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ReviewResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductReview;
import com.example.elsa_store.entity.ReviewVote;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.ReviewVoteRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** "Hữu ích": khách bấm cho đánh giá của người khác, không tự bấm cho mình; đếm lại từ bảng phiếu; sắp xếp theo hữu ích. */
class ReviewHelpfulTest {

    private final ProductReviewRepository reviews = mock(ProductReviewRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final ReviewVoteRepository votes = mock(ReviewVoteRepository.class);
    private final ProductReviewServiceImpl service = new ProductReviewServiceImpl(reviews, products,
            mock(OrderItemRepository.class), accessGuard, mock(FileStorageService.class), mock(NotificationService.class));

    private User author;
    private User reader;
    private ProductReview review;

    @BeforeEach
    void setUp() {
        service.setVoteRepository(votes);
        Store store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(1L);
        Product shoes = Product.builder().name("Giày").status(1).store(store).build();
        shoes.setId(10L);
        author = User.builder().username("khach001").role(Role.USER).build();
        author.setId(7L);
        reader = User.builder().username("khach002").role(Role.USER).build();
        reader.setId(8L);
        review = ProductReview.builder().product(shoes).user(author).rating(5).build();
        review.setId(50L);
        when(products.findById(10L)).thenReturn(Optional.of(shoes));
        when(reviews.findById(50L)).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accessGuard.currentUser()).thenReturn(reader);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(reader));
    }

    @Test
    void votingAddsOnceAndUnvotingRemoves() {
        when(votes.findByReview_IdAndUser_Id(50L, 8L)).thenReturn(Optional.empty());
        when(votes.countByReview_Id(50L)).thenReturn(1L);
        ReviewResponse res = service.markHelpful(50L, true);
        assertEquals(1, res.getHelpfulCount());
        assertTrue(res.getVotedHelpful());
        verify(votes).save(any(ReviewVote.class));

        ReviewVote mine = ReviewVote.builder().review(review).user(reader).build();
        when(votes.findByReview_IdAndUser_Id(50L, 8L)).thenReturn(Optional.of(mine));
        service.markHelpful(50L, true);
        verify(votes, times(1)).save(any(ReviewVote.class));

        when(votes.countByReview_Id(50L)).thenReturn(0L);
        ReviewResponse off = service.markHelpful(50L, false);
        verify(votes).delete(mine);
        assertEquals(0, off.getHelpfulCount());
        assertFalse(off.getVotedHelpful());
    }

    @Test
    void ownReviewsAndStaffCannotVote() {
        when(accessGuard.currentUser()).thenReturn(author);
        assertThrows(AppException.class, () -> service.markHelpful(50L, true));
        when(accessGuard.currentUser()).thenReturn(User.builder().username("nv").role(Role.STAFF).build());
        assertThrows(AppException.class, () -> service.markHelpful(50L, true));
        verify(votes, never()).save(any());
    }

    @Test
    void helpfulSortUsesItsOwnQueryAndMarksTheViewersVotes() {
        review.setHelpfulCount(4);
        when(reviews.findForProductByHelpful(eq(10L), isNull(), eq(false), any())).thenReturn(new PageImpl<>(List.of(review)));
        when(votes.votedReviewIds(8L, List.of(50L))).thenReturn(List.of(50L));

        var page = service.list(10L, null, false, true, 0, 10);

        assertEquals(4, page.getContent().get(0).getHelpfulCount());
        assertTrue(page.getContent().get(0).getVotedHelpful());
        verify(reviews, never()).findForProduct(any(), any(), anyBoolean(), any());
    }
}
