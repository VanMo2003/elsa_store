package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.entity.DeviceToken;
import com.example.elsa_store.entity.Notification;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PushRequestedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.NotificationMapper;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.NotificationService;
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
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

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
    public void notifyStaff(
            NotificationType type, NotificationMessages.Message message, String link, Long referenceId) {
        send(userRepository.findAllByRoleIn(List.of(Role.ADMIN, Role.STAFF)), type, message, link, referenceId);
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
