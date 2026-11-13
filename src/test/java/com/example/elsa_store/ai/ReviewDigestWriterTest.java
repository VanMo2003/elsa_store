package com.example.elsa_store.ai;

import com.example.elsa_store.dto.response.ReviewDigestResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductReview;
import com.example.elsa_store.entity.ReviewDigest;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.ReviewDigestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Khách nói gì": đủ đánh giá mới tóm tắt, lưu lại theo chữ ký, bản cũ khi AI tắt / lỗi, không bao giờ tự bịa. */
class ReviewDigestWriterTest {

    private static ProductReview review(long id, int rating, String comment) {
        ProductReview r = new ProductReview();
        r.setId(id);
        r.setRating(rating);
        r.setComment(comment);
        r.setVariantLabel("Đen · 42");
        return r;
    }

    private static final List<ProductReview> REVIEWS = List.of(
            review(4, 5, "Giày êm, đi cả ngày không đau"),
            review(3, 4, "Êm nhưng form hơi nhỏ, nên lấy lên 1 size"),
            review(2, 5, "Giao nhanh, giày nhẹ"),
            review(1, 3, "ok"));

    private record Repos(ProductReviewRepository reviews, ReviewDigestRepository digests) {}

    private static Repos repos(List<ProductReview> reviews, ReviewDigest cached) {
        ProductReviewRepository reviewRepo = mock(ProductReviewRepository.class);
        when(reviewRepo.findByProduct_IdOrderByIdDesc(9L)).thenReturn(reviews);
        ReviewDigestRepository digestRepo = mock(ReviewDigestRepository.class);
        when(digestRepo.findByProduct_Id(9L)).thenReturn(Optional.ofNullable(cached));
        when(digestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new Repos(reviewRepo, digestRepo);
    }

    private static ReviewDigestWriter writer(AiClient ai, Repos repos) {
        ProductRepository products = mock(ProductRepository.class);
        Product product = new Product();
        product.setId(9L);
        product.setName("Giày chạy bộ Elsa");
        when(products.findById(9L)).thenReturn(Optional.of(product));
        return new ReviewDigestWriter(ai, products, repos.reviews(), repos.digests(), new AiRateLimiter());
    }

    private static AiClient off() {
        return new AiClient(new ObjectMapper(), "", "", "", "", "", "", "", "", "", 5);
    }

    private static ReviewDigest cached(String signature) {
        return ReviewDigest.builder().signature(signature).summary("Đa số khen êm.").pros("Êm chân\nNhẹ").cons("")
                .basedOn(3).generatedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).build();
    }

    @Test
    void promptListsCommentedReviewsWithStarsAndVariant() {
        String prompt = ReviewDigestWriter.prompt("Giày", REVIEWS.subList(0, 2));
        assertTrue(prompt.contains("Số đánh giá dưới đây: 2, trung bình 4.5/5 sao\n<reviews>\n"), prompt);
        assertTrue(prompt.contains("- 4 sao (Đen · 42): Êm nhưng form hơi nhỏ, nên lấy lên 1 size\n"), prompt);
    }

    @Test
    void signatureChangesOnNewOrEditedReviewsOnly() {
        String base = ReviewDigestWriter.signature(REVIEWS);
        assertEquals("4:4:-", base);
        List<ProductReview> edited = new ArrayList<>(REVIEWS);
        ProductReview changed = review(3, 4, "Sửa lại");
        changed.setEditedAt(LocalDateTime.of(2026, 11, 2, 8, 0));
        edited.set(1, changed);
        assertNotEquals(base, ReviewDigestWriter.signature(edited));
        ProductReview voted = review(2, 5, "Giao nhanh, giày nhẹ");
        voted.setHelpfulCount(10);
        List<ProductReview> helpful = new ArrayList<>(REVIEWS);
        helpful.set(2, voted);
        assertEquals(base, ReviewDigestWriter.signature(helpful));
    }

    @Test
    void tooFewCommentedReviewsMeansNoDigest() {
        Repos repos = repos(List.of(review(2, 5, "Tốt lắm"), review(1, 4, "ok")), null);
        assertNull(writer(off(), repos).digest(9L));
    }

    @Test
    void anUpToDateCachedDigestIsReturnedWithoutCallingTheModel() throws Exception {
        try (var stub = new StubModelServer()) {
            Repos repos = repos(REVIEWS, cached("4:4:-"));
            ReviewDigestResponse out = writer(stub.client(), repos).digest(9L);
            assertTrue(out.upToDate());
            assertEquals(List.of("Êm chân", "Nhẹ"), out.pros());
            assertEquals(List.of(), out.cons());
            assertTrue(stub.requests.isEmpty());
        }
    }

    @Test
    void withoutAiAStaleDigestIsMarkedAndNoneIsInvented() {
        assertFalse(writer(off(), repos(REVIEWS, cached("3:3:-"))).digest(9L).upToDate());
        assertNull(writer(off(), repos(REVIEWS, null)).digest(9L));
    }

    @Test
    void theModelWritesAndTheDigestIsSaved() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.toolCall("write_digest", "{\"summary\":\"Đa số khen êm và nhẹ; vài người thấy form nhỏ.\","
                    + "\"pros\":[\"Êm chân\",\"- Nhẹ\",\"Giao nhanh\",\"Thừa\"],\"cons\":[\"Form hơi nhỏ\"]}");
            Repos repos = repos(REVIEWS, cached("3:3:-"));
            ReviewDigestResponse out = writer(stub.client(), repos).digest(9L);
            assertTrue(out.upToDate());
            assertEquals("Đa số khen êm và nhẹ; vài người thấy form nhỏ.", out.summary());
            assertEquals(List.of("Êm chân", "Nhẹ", "Giao nhanh"), out.pros());
            assertEquals(List.of("Form hơi nhỏ"), out.cons());
            assertEquals(3, out.basedOn());
            verify(repos.digests()).save(any());
            String request = stub.requests.get(0).toString();
            assertTrue(request.contains("Giày êm, đi cả ngày không đau"), request);
            assertFalse(request.contains(": ok\\n"), request);
        }
    }

    @Test
    void aModelFailureKeepsTheOldDigest() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.then(400, "{}");
            Repos repos = repos(REVIEWS, cached("3:3:-"));
            ReviewDigestResponse out = writer(stub.client(), repos).digest(9L);
            assertFalse(out.upToDate());
            assertEquals("Đa số khen êm.", out.summary());
            verify(repos.digests(), never()).save(any());
        }
    }
}
