package com.example.elsa_store.service;

import java.util.List;
import java.util.Map;

public interface PushNotificationSender {
    /** False khi chưa cấu hình service account Firebase; thông báo khi đó chỉ có trong app. */
    boolean isEnabled();

    /** Gửi mỗi token một tin và trả về các token FCM báo là không còn đăng ký. */
    List<String> send(List<String> tokens, String title, String body, Map<String, String> data);
}
