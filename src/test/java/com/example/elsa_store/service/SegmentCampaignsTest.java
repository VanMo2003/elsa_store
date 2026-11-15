package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.SegmentOfferRequest;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import com.example.elsa_store.dto.response.SegmentCampaignResponse;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.PromotionRepository;
import com.example.elsa_store.repository.SegmentCampaignRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gửi ưu đãi cho nhóm khách: chỉ khách có tài khoản, mã phải đang chạy của cửa hàng, 24 giờ / nhóm. */
class SegmentCampaignsTest {

    private final AccessGuard guard = mock(AccessGuard.class);
    private final CustomerSegments segments = mock(CustomerSegments.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SegmentCampaignRepository campaigns = mock(SegmentCampaignRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SegmentCampaigns service =
            new SegmentCampaigns(guard, segments, promotions, users, campaigns, notifications);
    private final Store store = new Store();

    private static User user(long id, Role role, boolean enabled) {
        User u = User.builder().role(role).enabled(enabled).build();
        u.setId(id);
        return u;
    }

    private static CustomerSegmentsResponse.Item item(Long userId) {
        return new CustomerSegmentsResponse.Item(1L, userId, "Khách", null, null, 2, 500000, null, 70, CustomerSegment.AT_RISK);
    }

    @BeforeEach
    void setUp() {
        store.setId(1L);
        store.setName("Shop Elsa");
        when(guard.requireStoreManager()).thenReturn(store);
        when(guard.currentUser()).thenReturn(user(100, Role.STORE_OWNER, true));
        when(segments.list(CustomerSegment.AT_RISK)).thenReturn(new CustomerSegmentsResponse(
                Map.of(CustomerSegment.AT_RISK, 4L), List.of(item(11L), item(12L), item(null), item(11L))));
        when(users.findAllById(anyIterable())).thenReturn(List.of(user(11, Role.USER, true), user(12, Role.USER, false)));
        when(campaigns.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Promotion coupon(Store owner, Integer status, LocalDate end) {
        Promotion p = new Promotion();
        p.setStore(owner);
        p.setCouponCode("QUAYLAI");
        p.setStatus(status);
        p.setEndDate(end);
        return p;
    }

    @Test
    void sendsToEnabledAccountsOnceEachWithTheCoupon() {
        when(promotions.findById(9L)).thenReturn(Optional.of(coupon(store, 1, LocalDate.now())));
        SegmentCampaignResponse out = service.send(CustomerSegment.AT_RISK, new SegmentOfferRequest("  Lâu rồi chưa gặp! ", 9L));
        assertEquals(1, out.recipients(), "bỏ khách vãng lai, trùng lặp và tài khoản bị khoá");
        assertEquals("QUAYLAI", out.couponCode());
        assertEquals("Lâu rồi chưa gặp!", out.message());
        verify(notifications, times(1)).notify(argThat(u -> u.getId() == 11L), eq(NotificationType.STORE_OFFER),
                eq(new NotificationMessages.Message("Shop Elsa gửi ưu đãi cho bạn",
                        "Lâu rồi chưa gặp! Nhập mã QUAYLAI khi đặt hàng.")),
                eq("elsastore://stores/1"), eq(1L));
    }

    @Test
    void couponMustBeRunningAndOwned() {
        Store other = new Store();
        other.setId(2L);
        when(promotions.findById(1L)).thenReturn(Optional.of(coupon(other, 1, null)));
        when(promotions.findById(2L)).thenReturn(Optional.of(coupon(store, 0, null)));
        when(promotions.findById(3L)).thenReturn(Optional.of(coupon(store, 1, LocalDate.now().minusDays(1))));
        for (long id : new long[] {1, 2, 3, 4}) {
            var e = assertThrows(AppException.class,
                    () -> service.send(CustomerSegment.AT_RISK, new SegmentOfferRequest("Ưu đãi", id)));
            assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        }
        verify(notifications, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void onePerSegmentPerDay() {
        when(campaigns.existsByStore_IdAndSegmentAndCreatedAtAfter(eq(1L), eq(CustomerSegment.AT_RISK), any())).thenReturn(true);
        var e = assertThrows(AppException.class, () -> service.send(CustomerSegment.AT_RISK, new SegmentOfferRequest("Hi", null)));
        assertEquals(ErrorCode.CAMPAIGN_TOO_SOON, e.getErrorCode());
    }

    @Test
    void segmentWithoutAccountsIsRejected() {
        when(users.findAllById(anyIterable())).thenReturn(List.of());
        assertThrows(AppException.class, () -> service.send(CustomerSegment.AT_RISK, new SegmentOfferRequest("Hi", null)));
    }
}
