package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAvatarTest {

    @Test
    void avatarIsStoredForTheCallerAndCanBeRemoved() {
        UserRepository users = mock(UserRepository.class);
        AccessGuard accessGuard = mock(AccessGuard.class);
        FileStorageService storage = mock(FileStorageService.class);
        UserServiceImpl service = new UserServiceImpl(mock(WishlistItemRepository.class), users,
                mock(PasswordEncoder.class), mock(NotificationRepository.class), mock(DeviceTokenRepository.class),
                mock(CustomerRepository.class), mock(UserAccountFactory.class), accessGuard, storage,
                mock(AuthenticationService.class));
        User me = User.builder().username("khach01").role(Role.USER).build();
        me.setId(7L);
        when(accessGuard.currentUser()).thenReturn(me);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[] {1});
        when(storage.storeAvatar(file)).thenReturn("/uploads/avatars/a.png");

        UserResponse res = service.updateAvatar(file);
        assertEquals("/uploads/avatars/a.png", res.getAvatarUrl());
        assertEquals("/uploads/avatars/a.png", me.getAvatarUrl());
        assertNull(service.removeAvatar().getAvatarUrl());
    }
}
