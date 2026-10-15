package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.TestPushResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PushNotificationListener;
import com.example.elsa_store.event.PushRequestedEvent;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.PushNotificationSender;
import com.example.elsa_store.utils.AppLinks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** "Gửi thông báo thử": để người dùng tự kiểm tra máy có nhận push khi app đã tắt. */
class NotificationServiceImplTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PushNotificationSender sender = mock(PushNotificationSender.class);
    private final PushNotificationListener listener = mock(PushNotificationListener.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(
            notifications, devices, users, mock(ApplicationEventPublisher.class), sender, listener);

    @BeforeEach
    void signIn() {
        User user = User.builder().username("khach001").build();
        user.setId(7L);
        when(users.findByUsername("khach001")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("khach001", null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendsTheTestPushToTheCallersOwnDevicesWithoutAnInboxRow() {
        when(sender.isEnabled()).thenReturn(true);
        when(devices.findTokensByUserIds(List.of(7L))).thenReturn(List.of("t1", "t2"));

        TestPushResponse response = service.sendTestPush(0);

        assertTrue(response.isPushEnabled());
        assertEquals(2, response.getDevices());
        assertEquals(0, response.getDelaySeconds());
        ArgumentCaptor<PushRequestedEvent> event = ArgumentCaptor.forClass(PushRequestedEvent.class);
        verify(listener, timeout(2000)).onPushRequested(event.capture());
        assertEquals(List.of(7L), event.getValue().userIds());
        assertEquals("Thông báo thử", event.getValue().title());
        assertEquals("TEST_PUSH", event.getValue().data().get("type"));
        assertEquals(AppLinks.notifications(), event.getValue().data().get("link"));
        verifyNoInteractions(notifications);
    }

    @Test
    void delayIsClampedToZeroThroughSixty() {
        when(sender.isEnabled()).thenReturn(false);

        assertEquals(0, service.sendTestPush(-5).getDelaySeconds());
        assertEquals(60, service.sendTestPush(9999).getDelaySeconds());
        assertEquals(10, service.sendTestPush(10).getDelaySeconds());
    }

    @Test
    void doesNothingWhenFcmIsOffOnTheServer() {
        when(sender.isEnabled()).thenReturn(false);
        when(devices.findTokensByUserIds(List.of(7L))).thenReturn(List.of("t1"));

        TestPushResponse response = service.sendTestPush(0);

        assertFalse(response.isPushEnabled());
        assertEquals(1, response.getDevices());
        verify(listener, after(300).never()).onPushRequested(any());
    }

    @Test
    void doesNothingWhenTheAccountHasNoRegisteredDevice() {
        when(sender.isEnabled()).thenReturn(true);
        when(devices.findTokensByUserIds(List.of(7L))).thenReturn(List.of());

        TestPushResponse response = service.sendTestPush(0);

        assertTrue(response.isPushEnabled());
        assertEquals(0, response.getDevices());
        verify(listener, never()).onPushRequested(any());
        verify(listener, after(300).never()).onPushRequested(any());
    }
}
