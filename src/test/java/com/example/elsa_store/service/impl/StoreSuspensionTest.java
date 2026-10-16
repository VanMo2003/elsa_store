package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.dto.request.StoreRejectRequest;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** ADMIN đình chỉ / mở lại cửa hàng: chỉ cửa hàng đã duyệt; ẩn sản phẩm khỏi khách; báo chủ và quản lý một lần. */
class StoreSuspensionTest {

    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StoreRegistrationServiceImpl service = new StoreRegistrationServiceImpl(stores,
            mock(UserAccountFactory.class), accessGuard, notifications, mock(ApplicationEventPublisher.class));

    private Store store;

    @BeforeEach
    void setUp() {
        User owner = User.builder().username("chu").build();
        owner.setId(2L);
        store = Store.builder().name("Elsa Q1").owner(owner).approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(3L);
        when(stores.findById(3L)).thenReturn(Optional.of(store));
        when(stores.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void suspendingHidesTheStoreAndItsProductsFromCustomers() {
        Product shoes = Product.builder().name("Giày").status(1).store(store).build();
        assertTrue(ProductServiceImpl.isPublic(shoes));

        StoreResponse res = service.suspend(3L, new StoreRejectRequest("  Bán hàng giả  "));

        assertTrue(res.getSuspended());
        assertEquals("Bán hàng giả", res.getSuspendReason());
        assertNotNull(res.getSuspendedAt());
        assertFalse(store.isOpenToCustomers());
        assertFalse(ProductServiceImpl.isPublic(shoes));
        verify(notifications).notifyStoreManagers(eq(store), eq(NotificationType.STORE_SUSPENDED),
                argThat(m -> m.body().contains("Bán hàng giả")), any(), eq(3L));

        service.suspend(3L, new StoreRejectRequest("Bán hàng giả"));
        verify(notifications, times(1)).notifyStoreManagers(any(), eq(NotificationType.STORE_SUSPENDED), any(), any(), any());
    }

    @Test
    void reinstatingClearsTheReasonAndIsIdempotent() {
        service.suspend(3L, new StoreRejectRequest("Vi phạm"));
        StoreResponse res = service.reinstate(3L);

        assertFalse(res.getSuspended());
        assertNull(res.getSuspendReason());
        assertTrue(store.isOpenToCustomers());
        service.reinstate(3L);
        verify(notifications, times(1)).notifyStoreManagers(any(), eq(NotificationType.STORE_REINSTATED), any(), any(), any());
    }

    @Test
    void onlyApprovedStoresAndOnlyAdmins() {
        store.setApprovalStatus(ApprovalStatus.PENDING);
        assertThrows(AppException.class, () -> service.suspend(3L, new StoreRejectRequest("x")));

        when(accessGuard.requireAdmin()).thenThrow(new AppException(com.example.elsa_store.exception.ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.reinstate(3L));
        verify(stores, never()).save(any());
    }
}
