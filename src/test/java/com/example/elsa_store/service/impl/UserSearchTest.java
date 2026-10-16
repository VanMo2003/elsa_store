package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** ADMIN tìm tài khoản: từ khoá viết thường, bỏ ký tự đại diện của LIKE; trống thì không lọc. */
class UserSearchTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final UserServiceImpl service = new UserServiceImpl(mock(WishlistItemRepository.class), users,
            mock(PasswordEncoder.class), mock(NotificationRepository.class), mock(DeviceTokenRepository.class),
            mock(CustomerRepository.class), mock(UserAccountFactory.class), accessGuard, mock(FileStorageService.class),
            mock(AuthenticationService.class));

    @Test
    void keywordBecomesASafeLowercasePattern() {
        when(users.search(any(), any(), any())).thenReturn(Page.empty());
        service.getAll(Role.USER, "  Nguyen_50% ", 0, 20);
        verify(users).search(eq(Role.USER), eq("%nguyen50%"), any());
        service.getAll(null, "   ", 0, 20);
        verify(users).search(isNull(), isNull(), any());
        verify(accessGuard, times(2)).requireAdmin();
    }
}
