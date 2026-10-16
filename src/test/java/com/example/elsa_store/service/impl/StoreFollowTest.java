package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.StoreFollow;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.StoreFollowRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Theo dõi cửa hàng: chỉ khách, chỉ cửa hàng đã duyệt, bấm lại không trùng; sản phẩm mới báo một lần. */
class StoreFollowTest {

    private final StoreFollowRepository follows = mock(StoreFollowRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final StoreService storeService = mock(StoreService.class);
    private final StoreFollowServiceImpl service = new StoreFollowServiceImpl(follows, stores, accessGuard, storeService);
    private final NotificationService notifications = mock(NotificationService.class);
    private final NewProductAlerts alerts = new NewProductAlerts(follows, notifications);

    private User fan;
    private Store store;

    @BeforeEach
    void setUp() {
        fan = User.builder().username("khach01").role(Role.USER).build();
        fan.setId(7L);
        store = Store.builder().name("Elsa Q1").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(3L);
        when(accessGuard.currentUser()).thenReturn(fan);
        when(stores.findById(3L)).thenReturn(Optional.of(store));
    }

    @Test
    void followingIsIdempotentAndOnlyForCustomersOfOpenStores() {
        service.follow(3L);
        verify(follows).save(argThat(f -> f.getUser() == fan && f.getStore() == store));
        when(follows.existsByUser_IdAndStore_Id(7L, 3L)).thenReturn(true);
        service.follow(3L);
        verify(follows, times(1)).save(any());

        store.setApprovalStatus(ApprovalStatus.PENDING);
        assertThrows(ResourceNotFoundException.class, () -> service.follow(3L));

        User staff = User.builder().username("nv").role(Role.STAFF).build();
        when(accessGuard.currentUser()).thenReturn(staff);
        assertThrows(AppException.class, () -> service.follow(3L));
    }

    @Test
    void followersHearAboutTheFirstVariantOfAVisibleProductOnly() {
        Product shoes = Product.builder().name("Giày mới").status(1).store(store).build();
        shoes.setId(10L);
        when(follows.findAllByStore_Id(3L)).thenReturn(List.of(StoreFollow.builder().user(fan).store(store).build()));

        alerts.afterVariantAdded(shoes, 1);
        verify(notifications).notify(eq(fan), eq(NotificationType.NEW_PRODUCT),
                argThat(m -> m.title().contains("Elsa Q1") && m.body().contains("Giày mới")),
                eq("elsastore://products/10"), eq(10L));

        alerts.afterVariantAdded(shoes, 2);
        shoes.setStatus(0);
        alerts.afterVariantAdded(shoes, 1);
        verify(notifications, times(1)).notify(any(), any(), any(), any(), any());
    }
}
