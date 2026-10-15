package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CoinTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Thưởng xu đánh giá: mỗi khoản một lần cho mỗi sản phẩm, chỉ tài khoản khách. */
class ReviewRewardsTest {

    private final CoinWallet wallet = mock(CoinWallet.class);
    private final CoinTransactionRepository book = mock(CoinTransactionRepository.class);
    private final ReviewRewards rewards = new ReviewRewards(wallet, book, 100, 200);

    private User buyer;
    private Product shoes;

    @BeforeEach
    void setUp() {
        buyer = User.builder().username("khach01").role(Role.USER).build();
        buyer.setId(7L);
        shoes = Product.builder().name("Giày").build();
        shoes.setId(10L);
    }

    @Test
    void reviewAndPhotoRewardsArePaidOncePerProduct() {
        assertEquals(300, rewards.claimable(buyer, shoes));
        assertEquals(100, rewards.afterReview(buyer, shoes));
        verify(wallet).credit(eq(buyer), eq(100), eq(CoinTransactionType.REVIEW_REWARD), eq(shoes), anyString());

        when(book.existsByUser_IdAndProduct_IdAndType(7L, 10L, CoinTransactionType.REVIEW_REWARD)).thenReturn(true);
        assertEquals(0, rewards.afterReview(buyer, shoes), "sửa / viết lại không nhận lại");
        assertEquals(200, rewards.claimable(buyer, shoes));

        assertEquals(200, rewards.afterPhotos(buyer, shoes));
        when(book.existsByUser_IdAndProduct_IdAndType(7L, 10L, CoinTransactionType.PHOTO_REWARD)).thenReturn(true);
        assertEquals(0, rewards.afterPhotos(buyer, shoes));
        assertEquals(0, rewards.claimable(buyer, shoes));
        verify(wallet, times(2)).credit(any(), anyInt(), any(), any(), anyString());
    }

    @Test
    void storeAccountsGetNothing() {
        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(8L);
        assertEquals(0, rewards.claimable(staff, shoes));
        assertEquals(0, rewards.afterReview(staff, shoes));
        verifyNoInteractions(wallet);
    }
}
