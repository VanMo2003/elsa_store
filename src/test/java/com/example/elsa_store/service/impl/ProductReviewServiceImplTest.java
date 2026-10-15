package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ReviewReplyRequest;
import com.example.elsa_store.dto.request.ReviewRequest;
import com.example.elsa_store.dto.response.ReviewEligibilityResponse;
import com.example.elsa_store.dto.response.ReviewResponse;
import com.example.elsa_store.dto.response.ReviewSummaryResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductReviewServiceImplTest {

    private final ProductReviewRepository reviews = mock(ProductReviewRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ProductReviewServiceImpl service =
            new ProductReviewServiceImpl(reviews, products, orderItems, accessGuard, storage);

    private Store store;
    private Product shoes;
    private User buyer;
    private User staff;
    private OrderItem purchase;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Store A").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(1L);
        shoes = Product.builder().name("Giay").status(1).store(store).build();
        shoes.setId(10L);
        buyer = User.builder().username("khach001").role(Role.USER).build();
        buyer.setId(7L);
        staff = User.builder().username("nhanvien1").role(Role.STAFF).build();
        staff.setId(8L);
        ProductVariant variant = ProductVariant.builder().product(shoes).color("Den").size("42").build();
        Order order = new Order();
        order.setCode("ORD-261015-ABCDEF");
        purchase = new OrderItem();
        purchase.setOrder(order);
        purchase.setProductVariant(variant);
        when(products.findById(10L)).thenReturn(Optional.of(shoes));
        when(accessGuard.currentUser()).thenReturn(buyer);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(buyer));
        when(orderItems.completedPurchases(eq(7L), eq(10L), any())).thenReturn(List.of(purchase));
        when(reviews.findByUser_IdAndProduct_Id(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(reviews.save(any(ProductReview.class))).thenAnswer(inv -> {
            ProductReview r = inv.getArgument(0);
            if (r.getId() == null) r.setId(99L);
            return r;
        });
    }

    @Test
    void buyerWithACompletedOrderCanReview() {
        ReviewResponse res = service.upsert(10L, new ReviewRequest(5, "  Rat em chan  "));
        assertEquals(5, res.getRating());
        assertEquals("Rat em chan", res.getComment());
        assertEquals("Den / 42", res.getVariantLabel());
        assertEquals("k******1", res.getAuthorName(), "tên đăng nhập được che");
        assertTrue(res.getMine());
        assertFalse(res.getEdited());
    }

    @Test
    void photosAttachToTheBuyersOwnReviewUpToFive() {
        ProductReview mine = ProductReview.builder().user(buyer).product(shoes).rating(5).build();
        mine.setId(50L);
        when(storage.storeReviewImage(any())).thenAnswer(inv ->
                "/uploads/reviews/" + ((MultipartFile) inv.getArgument(0)).getOriginalFilename());
        MultipartFile a = new MockMultipartFile("files", "a.png", "image/png", new byte[] {1});
        MultipartFile b = new MockMultipartFile("files", "b.png", "image/png", new byte[] {1});

        assertThrows(ResourceNotFoundException.class, () -> service.addImages(10L, List.of(a)),
                "chưa viết đánh giá thì chưa thêm ảnh được");

        when(reviews.findByUser_IdAndProduct_Id(7L, 10L)).thenReturn(Optional.of(mine));
        ReviewResponse res = service.addImages(10L, List.of(a, b));
        assertEquals(List.of("/uploads/reviews/a.png", "/uploads/reviews/b.png"), res.getImages());

        res = service.removeImage(10L, "/uploads/reviews/a.png");
        assertEquals(List.of("/uploads/reviews/b.png"), res.getImages());
        assertThrows(ResourceNotFoundException.class, () -> service.removeImage(10L, "/uploads/reviews/x.png"));

        List<MultipartFile> five = List.of(a, a, a, a, a);
        assertThrows(AppException.class, () -> service.addImages(10L, five), "1 + 5 > 5 ảnh");
        assertEquals(1, mine.getImages().size());
    }

    @Test
    void writingAgainEditsTheSameReview() {
        ProductReview existing = ProductReview.builder().user(buyer).product(shoes).rating(2).comment("Tam").build();
        existing.setId(50L);
        when(reviews.findByUser_IdAndProduct_Id(7L, 10L)).thenReturn(Optional.of(existing));

        ReviewResponse res = service.upsert(10L, new ReviewRequest(4, ""));
        assertEquals(50L, res.getId());
        assertEquals(4, res.getRating());
        assertNull(res.getComment());
        assertTrue(res.getEdited());
    }

    @Test
    void onlyCustomersWhoReceivedTheProductCanReview() {
        when(orderItems.completedPurchases(eq(7L), eq(10L), any())).thenReturn(List.of());
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED,
                assertThrows(AppException.class, () -> service.upsert(10L, new ReviewRequest(5, null))).getErrorCode());

        when(accessGuard.currentUser()).thenReturn(staff);
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED,
                assertThrows(AppException.class, () -> service.upsert(10L, new ReviewRequest(5, null))).getErrorCode());
        verify(reviews, never()).save(any());
    }

    @Test
    void eligibilityExplainsWhy() {
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.empty());
        assertEquals("NOT_SIGNED_IN", service.eligibility(10L).getReason());

        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(staff));
        assertEquals("NOT_CUSTOMER", service.eligibility(10L).getReason());

        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(buyer));
        ReviewEligibilityResponse ok = service.eligibility(10L);
        assertTrue(ok.getCanReview());
        assertNull(ok.getReview());

        when(orderItems.completedPurchases(eq(7L), eq(10L), any())).thenReturn(List.of());
        assertEquals("NOT_PURCHASED", service.eligibility(10L).getReason());
    }

    @Test
    void summaryHasAverageAndFullDistribution() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {5, 3L});
        rows.add(new Object[] {4, 1L});
        rows.add(new Object[] {1, 1L});
        when(reviews.distribution(10L)).thenReturn(rows);
        when(reviews.countWithImages(10L)).thenReturn(2L);

        ReviewSummaryResponse res = service.summary(10L);
        assertEquals(5L, res.getCount());
        assertEquals(2L, res.getWithImages());
        assertEquals(4.0, res.getAverage()); // (15 + 4 + 1) / 5
        assertEquals(List.of("5", "4", "3", "2", "1"), new ArrayList<>(res.getDistribution().keySet()));
        assertEquals(0L, res.getDistribution().get("3"));

        when(reviews.distribution(10L)).thenReturn(List.of());
        assertNull(service.summary(10L).getAverage());
    }

    @Test
    void hiddenProductsHaveNoPublicReviews() {
        shoes.setStatus(0);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.summary(10L));
        assertThrows(AppException.class, () -> service.list(10L, 9, false, 0, 10));
    }

    @Test
    void storeStaffCanReplyAndOnlyTheAuthorCanDelete() {
        ProductReview review = ProductReview.builder().user(buyer).product(shoes).rating(3).build();
        review.setId(50L);
        when(reviews.findById(50L)).thenReturn(Optional.of(review));
        when(accessGuard.authorize(store, Access.STAFF_WRITE)).thenReturn(staff);

        ReviewResponse res = service.reply(50L, new ReviewReplyRequest(" Cam on ban! "));
        assertEquals("Cam on ban!", res.getStoreReply());
        assertNotNull(res.getRepliedAt());
        assertSame(staff, review.getRepliedBy());
        assertFalse(res.getEdited(), "trả lời không tính là khách sửa đánh giá");

        when(accessGuard.currentUser()).thenReturn(staff);
        assertThrows(AppException.class, () -> service.delete(50L));
        when(accessGuard.currentUser()).thenReturn(buyer);
        service.delete(50L);
        verify(reviews).delete(review);
    }

    @Test
    void namesAreMaskedSafely() {
        assertEquals("a*", ProductReviewServiceImpl.maskedName("ab"));
        assertEquals("k******z", ProductReviewServiceImpl.maskedName("khachhang_xyz"));
        assertEquals("Khách hàng", ProductReviewServiceImpl.maskedName(" "));
    }
}
