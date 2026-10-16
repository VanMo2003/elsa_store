package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationGroup;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.NotificationSettingsRequest;
import com.example.elsa_store.dto.response.NotificationSettingsResponse;
import com.example.elsa_store.entity.Notification;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PushNotificationListener;
import com.example.elsa_store.event.PushRequestedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.PushNotificationSender;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tắt nhóm thông báo: người đã tắt không nhận (cả trong app lẫn push); thông báo đơn hàng luôn gửi. */
class NotificationSettingsTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(notifications,
            mock(DeviceTokenRepository.class), users, events, mock(PushNotificationSender.class),
            mock(PushNotificationListener.class));
    private final NotificationMessages.Message message = new NotificationMessages.Message("t", "b");

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private User signIn(String username, Role role) {
        User user = User.builder().username(username).role(role).build();
        user.setId(username.length() + 0L);
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of()));
        return user;
    }

    @Test
    void mutedGroupsAreSkippedButOrderNewsAlwaysArrive() {
        User fan = User.builder().username("fan").role(Role.USER).build();
        fan.setId(1L);
        fan.setMutedGroups(EnumSet.of(NotificationGroup.PRICE_DROP));
        assertEquals("PRICE_DROP", fan.getMutedNotifications());

        service.notify(fan, NotificationType.PRICE_DROP, message, null, 10L);
        verifyNoInteractions(notifications, events);

        service.notify(fan, NotificationType.ORDER_CONFIRMED, message, null, 10L);
        verify(notifications).saveAll(argThatHasUser(fan));
        verify(events).publishEvent(any(PushRequestedEvent.class));
    }

    @Test
    void storeMembersWhoMutedFeedbackAreLeftOutOfStoreWideNews() {
        User owner = User.builder().username("chu").role(Role.STORE_OWNER).build();
        owner.setId(2L);
        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(3L);
        staff.setMutedGroups(EnumSet.of(NotificationGroup.CUSTOMER_FEEDBACK));
        Store store = Store.builder().name("Elsa").owner(owner).build();
        store.setId(5L);
        when(users.findAllByStore_Id(5L)).thenReturn(List.of(staff));

        service.notifyStore(store, NotificationType.QUESTION_NEW, message, null, 1L);
        ArgumentCaptor<PushRequestedEvent> push = ArgumentCaptor.forClass(PushRequestedEvent.class);
        verify(events).publishEvent(push.capture());
        assertEquals(List.of(2L), push.getValue().userIds());

        service.notifyStore(store, NotificationType.NEW_ORDER, message, null, 1L);
        verify(events, times(2)).publishEvent(push.capture());
        assertEquals(List.of(2L, 3L), push.getValue().userIds());
    }

    @Test
    void customersAndStaffSeeTheirOwnGroupsAndUnknownGroupsAreRejected() {
        User fan = signIn("khach01", Role.USER);
        NotificationSettingsResponse res = service.updateSettings(
                new NotificationSettingsRequest(Map.of("NEW_PRODUCT", false, "BACK_IN_STOCK", true)));
        assertEquals(List.of("PRICE_DROP", "NEW_PRODUCT", "BACK_IN_STOCK", "STORE_REPLIES"),
                res.getGroups().stream().map(NotificationSettingsResponse.Group::group).toList());
        assertFalse(res.getGroups().get(1).enabled());
        assertTrue(res.getGroups().get(0).enabled());
        assertTrue(fan.mutes(NotificationType.NEW_PRODUCT));

        service.updateSettings(new NotificationSettingsRequest(Map.of("NEW_PRODUCT", true)));
        assertNull(fan.getMutedNotifications());
        assertThrows(AppException.class,
                () -> service.updateSettings(new NotificationSettingsRequest(Map.of("CUSTOMER_FEEDBACK", false))));

        signIn("nhanvien", Role.STAFF);
        assertEquals(List.of(new NotificationSettingsResponse.Group("CUSTOMER_FEEDBACK", true)),
                service.getSettings().getGroups());
        signIn("admin", Role.ADMIN);
        assertTrue(service.getSettings().getGroups().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Iterable<Notification> argThatHasUser(User user) {
        return org.mockito.ArgumentMatchers.argThat(
                rows -> rows instanceof List<?> list && list.size() == 1 && ((Notification) list.get(0)).getUser() == user);
    }
}
