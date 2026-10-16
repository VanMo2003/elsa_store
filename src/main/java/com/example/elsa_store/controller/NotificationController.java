
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.request.NotificationSettingsRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.NotificationSettingsResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.TestPushResponse;
import com.example.elsa_store.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.getMine(page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationCountResponse> countUnread() {
        return ApiResponse.ok(notificationService.countUnread());
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok(null);
    }

    /** Đăng ký token FCM của thiết bị này cho tài khoản đang đăng nhập. */
    @PostMapping("/devices")
    public ApiResponse<Void> registerDevice(@Valid @RequestBody DeviceTokenRequest req) {
        notificationService.registerDevice(req);
        return ApiResponse.ok(null);
    }

    /**
     * Gửi thông báo thử tới thiết bị của chính người gọi sau một lúc (mặc định 10 giây) để kiểm tra việc nhận push khi
     * app đã tắt.
     */
    @PostMapping("/devices/test")
    public ApiResponse<TestPushResponse> sendTestPush(@RequestParam(defaultValue = "10") int delaySeconds) {
        return ApiResponse.ok(notificationService.sendTestPush(delaySeconds));
    }

    /** Nhóm thông báo tắt được (giảm giá, sản phẩm mới, có hàng lại, cửa hàng trả lời; đánh giá / câu hỏi mới). */
    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> settings() {
        return ApiResponse.ok(notificationService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(@Valid @RequestBody NotificationSettingsRequest req) {
        return ApiResponse.ok(notificationService.updateSettings(req));
    }

    /** Gọi trước khi đăng xuất để thiết bị thôi nhận thông báo của tài khoản này. */
    @DeleteMapping("/devices")
    public ApiResponse<Void> unregisterDevice(@RequestParam String token) {
        notificationService.unregisterDevice(token);
        return ApiResponse.ok(null);
    }
}
