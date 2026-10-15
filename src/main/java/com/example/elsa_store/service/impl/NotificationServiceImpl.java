package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.TestPushResponse;
import com.example.elsa_store.entity.DeviceToken;
import com.example.elsa_store.entity.Notification;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PushNotificationListener;
import com.example.elsa_store.event.PushRequestedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.NotificationMapper;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.PushNotificationSender;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TEST_PUSH_DELAY_SECONDS = 60;

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PushNotificationSender pushNotificationSender;
    private final PushNotificationListener pushNotificationListener;

    @Override
    public void notify(
            User user,
            NotificationType type,
            NotificationMessages.Message message,
            String link,
            Long referenceId) {
        send(List.of(user), type, message, link, referenceId);
    }

    @Override
    public void notifyStore(
            Store store,
            NotificationType type,
            NotificationMessages.Message message,
            String link,
            Long referenceId) {
        Map<Long, User> recipients = new LinkedHashMap<>();
        if (store.getOwner() != null) {
            recipients.put(store.getOwner().getId(), store.getOwner());
        }
        userRepository.findAllByStore_Id(store.getId()).forEach(member -> recipients.put(member.getId(), member));
        send(recipients.values().stream().filter(User::isEnabled).toList(), type, message, link, referenceId);
    }

    @Override
    public void notifyAdmins(
            NotificationType type, NotificationMessages.Message message, String link, Long referenceId) {
        send(userRepository.findAllByRole(Role.ADMIN), type, message, link, referenceId);
    }

    private void send(
            List<User> recipients,
            NotificationType type,
            NotificationMessages.Message message,
            String link,
            Long referenceId) {
        if (recipients.isEmpty()) {
            return;
        }
        notificationRepository.saveAll(recipients.stream()
                .map(user -> Notification.builder()
                        .user(user)
                        .type(type)
                        .title(message.title())
                        .body(message.body())
                        .link(link)
                        .referenceId(referenceId)
                        .build())
                .toList());

        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (link != null) {
            data.put("link", link);
        }
        if (referenceId != null) {
            data.put("referenceId", referenceId.toString());
        }
        // Đẩy sau khi commit (PushNotificationListener) để đơn bị rollback không bao giờ tới điện thoại.
        eventPublisher.publishEvent(new PushRequestedEvent(
                recipients.stream().map(User::getId).toList(), message.title(), message.body(), data));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMine(int page, int size) {
        User current = currentUser();
        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(notificationRepository
                .findAllByUser_Id(current.getId(), pageable)
                .map(NotificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationCountResponse countUnread() {
        User current = currentUser();
        return NotificationCountResponse.builder()
                .unread(notificationRepository.countByUser_IdAndReadFalse(current.getId()))
                .build();
    }

    @Override
    public NotificationResponse markRead(Long id) {
        User current = currentUser();
        Notification notification = notificationRepository
                .findByIdAndUser_Id(id, current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found id=" + id));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return NotificationMapper.toResponse(notification);
    }

    @Override
    public void markAllRead() {
        notificationRepository.markAllRead(currentUser().getId());
    }

    @Override
    public void registerDevice(DeviceTokenRequest req) {
        User current = currentUser();
        String token = req.getToken().trim();
        // Thiết bị thuộc về người đăng nhập sau cùng.
        DeviceToken device = deviceTokenRepository
                .findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).build());
        device.setUser(current);
        device.setPlatform(req.getPlatform());
        deviceTokenRepository.save(device);
    }

    @Override
    @Transactional(readOnly = true)
    public TestPushResponse sendTestPush(int delaySeconds) {
        User current = currentUser();
        int delay = Math.max(0, Math.min(delaySeconds, MAX_TEST_PUSH_DELAY_SECONDS));
        int devices = deviceTokenRepository
                .findTokensByUserIds(List.of(current.getId()))
                .size();
        boolean enabled = pushNotificationSender.isEnabled();
        if (enabled && devices > 0) {
            NotificationMessages.Message message = NotificationMessages.testPush();
            PushRequestedEvent event = new PushRequestedEvent(
                    List.of(current.getId()),
                    message.title(),
                    message.body(),
                    Map.of("type", "TEST_PUSH", "link", AppLinks.notifications()));
            // Gửi muộn để người thử kịp vuốt tắt app; đi thẳng tới sender, không tạo dòng trong hộp thông báo.
            CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS)
                    .execute(() -> pushNotificationListener.onPushRequested(event));
        }
        return TestPushResponse.builder()
                .pushEnabled(enabled)
                .devices(devices)
                .delaySeconds(delay)
                .build();
    }

    @Override
    public void unregisterDevice(String token) {
        User current = currentUser();
        deviceTokenRepository
                .findByTokenAndUser_Id(token.trim(), current.getId())
                .ifPresent(deviceTokenRepository::delete);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UN_AUTHENTICATED);
        }
        return userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.UN_AUTHENTICATED));
    }
}
