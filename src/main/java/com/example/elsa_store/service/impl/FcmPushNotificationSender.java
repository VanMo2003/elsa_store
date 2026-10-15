package com.example.elsa_store.service.impl;

import com.example.elsa_store.service.PushNotificationSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase Cloud Messaging qua HTTP v1 API, xác thực bằng khoá service account JSON ({@code FIREBASE_CREDENTIALS}).
 * Chưa có khoá thì sender tắt và thông báo chỉ có trong app.
 */
@Slf4j
@Component
public class FcmPushNotificationSender implements PushNotificationSender {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    /** Phải trùng với kênh app Flutter tạo trong MainActivity.kt (và meta-data trong AndroidManifest.xml). */
    static final String ANDROID_CHANNEL_ID = "elsa_store_alerts";

    private final RestClient restClient = RestClient.create();
    private final GoogleCredentials credentials;
    private final String projectId;

    public FcmPushNotificationSender(@Value("${firebase.credentials-path:}") String credentialsPath) {
        ServiceAccountCredentials serviceAccount = load(credentialsPath);
        this.credentials = serviceAccount == null ? null : serviceAccount.createScoped(List.of(SCOPE));
        this.projectId = serviceAccount == null ? null : serviceAccount.getProjectId();
    }

    @Override
    public boolean isEnabled() {
        return credentials != null && projectId != null;
    }

    @Override
    public List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
        String accessToken = accessToken();
        if (accessToken == null) {
            return List.of();
        }
        String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
        List<String> stale = new ArrayList<>();
        for (String token : tokens) {
            try {
                restClient
                        .post()
                        .uri(url)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", message(token, title, body, data)))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404
                        || e.getResponseBodyAsString().contains("UNREGISTERED")) {
                    stale.add(token);
                } else {
                    log.warn("FCM rejected a message ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                }
            } catch (RestClientException e) {
                log.warn("Cannot reach FCM: {}", e.getMessage());
            }
        }
        return stale;
    }

    /**
     * Tin nhắn FCM v1 gửi kèm khối {@code notification} để chính hệ điều hành hiện thông báo khi app chạy nền hoặc đã
     * tắt (app đang mở thì nhận qua {@code onMessage} và tự hiện toast).
     */
    static Map<String, Object> message(String token, String title, String body, Map<String, String> data) {
        Map<String, String> notification = new HashMap<>();
        notification.put("title", title);
        if (body != null) {
            notification.put("body", body);
        }
        Map<String, Object> android = new HashMap<>();
        // Mức ưu tiên cao đánh thức máy đang ở chế độ Doze, nên thông báo hiện được cả khi app chạy nền hoặc đã tắt.
        android.put("priority", "high");
        Map<String, Object> androidNotification = new HashMap<>();
        // Kênh do app tạo (MainActivity) ở mức ưu tiên cao: bật lên đầu màn hình kèm âm thanh thay vì nằm im trong khay.
        androidNotification.put("channel_id", ANDROID_CHANNEL_ID);
        androidNotification.put("notification_priority", "PRIORITY_HIGH");
        androidNotification.put("default_sound", true);
        // "tag" gom thông báo trên Android: tin mới cùng tag thay tin cũ (mỗi cuộc trò chuyện một thông báo).
        if (data != null && data.get("tag") != null) {
            androidNotification.put("tag", data.get("tag"));
            android.put("collapse_key", data.get("tag"));
        }
        android.put("notification", androidNotification);
        return Map.of(
                "token",
                token,
                "notification",
                notification,
                "data",
                data == null ? Map.of() : data,
                "android",
                android,
                "apns",
                Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
    }

    private synchronized String accessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.error("Cannot obtain an FCM access token", e);
            return null;
        }
    }

    private static ServiceAccountCredentials load(String path) {
        if (path == null || path.isBlank()) {
            log.info("FIREBASE_CREDENTIALS is not set - push notifications are off, in-app notifications still work");
            return null;
        }
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return ServiceAccountCredentials.fromStream(in);
        } catch (IOException | RuntimeException e) {
            log.error("Cannot read the Firebase service account at {} - push notifications are off", path, e);
            return null;
        }
    }
}
