package com.example.elsa_store.service;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.utils.NotificationMessages;

public interface NotificationService {
    /** Lưu thông báo trong app và đẩy tới thiết bị của user sau khi transaction commit. */
    void notify(
            User user,
            NotificationType type,
            NotificationMessages.Message message,
            String link,
            Long referenceId);

    /** Gửi cho mọi admin và nhân viên (ví dụ: có đơn hàng mới). */
    void notifyStaff(NotificationType type, NotificationMessages.Message message, String link, Long referenceId);

    PageResponse<NotificationResponse> getMine(int page, int size);

    NotificationCountResponse countUnread();

    NotificationResponse markRead(Long id);

    void markAllRead();

    void registerDevice(DeviceTokenRequest req);

    void unregisterDevice(String token);
}
