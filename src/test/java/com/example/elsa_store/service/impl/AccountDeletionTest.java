package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.DeleteAccountRequest;
import com.example.elsa_store.entity.Address;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Khách xoá tài khoản: cần mật khẩu, không còn đơn đang xử lý; thông tin cá nhân bị ẩn danh, tài khoản bị khoá. */
class AccountDeletionTest {

    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final WishlistItemRepository wishlist = mock(WishlistItemRepository.class);
    private final StoreFollowRepository follows = mock(StoreFollowRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
    private final AccountDeletion deletion = new AccountDeletion(accessGuard, encoder, users, orders, customers, carts,
            wishlist, follows, notifications, devices);

    private User fan;

    @BeforeEach
    void setUp() {
        fan = User.builder().username("khach01").email("k@x.vn").phone("0901").fullName("Nguyễn Văn A")
                .avatarUrl("/uploads/avatars/a.png").password("hash").role(Role.USER).build();
        fan.setId(7L);
        when(accessGuard.currentUser()).thenReturn(fan);
        when(encoder.matches("123456", "hash")).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("random-hash");
    }

    @Test
    void anonymisesPersonalDataAndLocksTheAccount() {
        Address home = new Address();
        home.setFullName("Nguyễn Văn A");
        home.setPhone("0901");
        home.setDetail("1 Lê Lợi");
        Customer profile = new Customer();
        profile.setFullName("Nguyễn Văn A");
        profile.setPhone("0901");
        profile.setAddresses(new ArrayList<>(List.of(home)));
        when(customers.findByUser_Id(7L)).thenReturn(Optional.of(profile));

        deletion.deleteMe(new DeleteAccountRequest("123456"));

        assertTrue(fan.getUsername().startsWith("deleted_7_"));
        assertNull(fan.getEmail());
        assertNull(fan.getPhone());
        assertNull(fan.getAvatarUrl());
        assertEquals(AccountDeletion.DELETED_NAME, fan.getFullName());
        assertEquals("random-hash", fan.getPassword());
        assertFalse(fan.isEnabled());
        assertEquals(1, fan.currentTokenVersion(), "mọi token cũ hết hiệu lực");
        assertNull(profile.getPhone());
        assertNull(home.getPhone());
        assertNull(home.getDetail());
        verify(devices).deleteAllByUserId(7L);
        verify(wishlist).deleteAllByUserId(7L);
        verify(notifications).deleteAllByUserId(7L);
        verify(users).save(fan);
    }

    @Test
    void wrongPasswordOrUnfinishedOrdersBlockDeletion() {
        AppException wrong = assertThrows(AppException.class, () -> deletion.deleteMe(new DeleteAccountRequest("sai")));
        assertEquals(ErrorCode.INCORRECT_CURRENT_PASSWORD, wrong.getErrorCode());

        when(orders.countByUser_IdAndStatusIn(eq(7L), any())).thenReturn(1L);
        AppException busy = assertThrows(AppException.class, () -> deletion.deleteMe(new DeleteAccountRequest("123456")));
        assertTrue(busy.getMessage().contains("orders in progress"));

        fan.setRole(Role.STORE_OWNER);
        assertThrows(AppException.class, () -> deletion.deleteMe(new DeleteAccountRequest("123456")));
        verify(users, never()).save(any());
        assertEquals("khach01", fan.getUsername());
    }
}
