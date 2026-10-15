package com.example.elsa_store.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tin nhắn FCM v1 mà app dựa vào để hiện thông báo khi chạy nền hoặc đã tắt. */
class FcmPushNotificationSenderTest {

    @Test
    @SuppressWarnings("unchecked")
    void chatMessageGoesToTheHighPriorityChannelAndReplacesTheThreadsLastOne() throws Exception {
        Map<String, Object> message = FcmPushNotificationSender.message(
                "device-token",
                "Store A",
                "Dạ em chào anh/chị",
                Map.of("type", "CHAT_MESSAGE", "link", "elsastore://chat/18", "tag", "chat-18"));

        assertEquals("device-token", message.get("token"));
        Map<String, Object> notification = (Map<String, Object>) message.get("notification");
        assertEquals("Store A", notification.get("title"));
        assertEquals("Dạ em chào anh/chị", notification.get("body"));
        Map<String, Object> android = (Map<String, Object>) message.get("android");
        assertEquals("high", android.get("priority"));
        assertEquals("chat-18", android.get("collapse_key"));
        Map<String, Object> androidNotification = (Map<String, Object>) android.get("notification");
        assertEquals(FcmPushNotificationSender.ANDROID_CHANNEL_ID, androidNotification.get("channel_id"));
        assertEquals("PRIORITY_HIGH", androidNotification.get("notification_priority"));
        assertEquals(true, androidNotification.get("default_sound"));
        assertEquals("chat-18", androidNotification.get("tag"));
        assertEquals("elsastore://chat/18", ((Map<String, String>) message.get("data")).get("link"));
        // iOS: có âm thanh khi app không mở.
        Map<String, Object> aps = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>) message.get("apns")).get("payload")).get("aps");
        assertEquals("default", aps.get("sound"));

        // Cho phép kiểm tay bằng cách gửi đúng payload này tới FCM với validate_only (-Dfcm.sample.out).
        String sampleOut = System.getProperty("fcm.sample.out");
        if (sampleOut != null) {
            Files.writeString(Path.of(sampleOut), new ObjectMapper().writeValueAsString(message));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificationWithoutTagStillUsesTheChannel() {
        Map<String, Object> message = FcmPushNotificationSender.message(
                "device-token",
                "Có đơn hàng mới",
                null,
                Map.of("type", "NEW_ORDER", "link", "elsastore://orders"));

        Map<String, Object> android = (Map<String, Object>) message.get("android");
        assertFalse(android.containsKey("collapse_key"));
        Map<String, Object> androidNotification = (Map<String, Object>) android.get("notification");
        assertEquals(FcmPushNotificationSender.ANDROID_CHANNEL_ID, androidNotification.get("channel_id"));
        assertFalse(androidNotification.containsKey("tag"));
        assertFalse(((Map<String, Object>) message.get("notification")).containsKey("body"));
    }

    @Test
    void channelMatchesTheOneTheAppCreates() throws Exception {
        // MainActivity.kt tạo kênh và AndroidManifest.xml khai báo làm mặc định — ba nơi phải cùng một id, nếu không
        // thông báo rơi vào kênh mặc định "Miscellaneous" và không bật lên màn hình.
        Path app = Path.of("..", "elsa_store_fe", "android", "app", "src", "main");
        Path mainActivity = app.resolve("kotlin/com/example/flutter_skeleton/MainActivity.kt");
        Path manifest = app.resolve("AndroidManifest.xml");
        if (!Files.exists(mainActivity) || !Files.exists(manifest)) {
            return; // chạy riêng repo BE, không có app Flutter bên cạnh
        }
        assertTrue(Files.readString(mainActivity).contains("\"" + FcmPushNotificationSender.ANDROID_CHANNEL_ID + "\""));
        assertTrue(Files.readString(manifest).contains("\"" + FcmPushNotificationSender.ANDROID_CHANNEL_ID + "\""));
    }
}
