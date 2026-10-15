package com.example.elsa_store.service;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.TestPushResponse;
import com.example.elsa_store.entity.Store;
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

    /** Gửi cho chủ, quản lý và nhân viên của một cửa hàng (ví dụ: có đơn hàng mới). */
    void notifyStore(
            Store store,
            NotificationType type,
            NotificationMessages.Message message,
            String link,
            Long referenceId);

    /** Gửi cho mọi ADMIN (ví dụ: có cửa hàng mới cần duyệt). */
    void notifyAdmins(NotificationType type, NotificationMessages.Message message, String link, Long referenceId);

    PageResponse<NotificationResponse> getMine(int page, int size);

    NotificationCountResponse countUnread();

    NotificationResponse markRead(Long id);

    void markAllRead();

    void registerDevice(DeviceTokenRequest req);

    /**
     * Gửi "Thông báo thử" tới thiết bị của chính người đang đăng nhập sau {@code delaySeconds} giây (0–60) để họ kịp
     * vuốt tắt app rồi xem máy có nhận không. Không tạo dòng trong hộp thông báo.
     */
    TestPushResponse sendTestPush(int delaySeconds);

    void unregisterDevice(String token);
}
