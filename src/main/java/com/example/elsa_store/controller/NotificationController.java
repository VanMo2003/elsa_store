
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.DeviceTokenRequest;
import com.example.elsa_store.dto.response.NotificationCountResponse;
import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.dto.response.PageResponse;
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

    /** Gọi trước khi đăng xuất để thiết bị thôi nhận thông báo của tài khoản này. */
    @DeleteMapping("/devices")
    public ApiResponse<Void> unregisterDevice(@RequestParam String token) {
        notificationService.unregisterDevice(token);
        return ApiResponse.ok(null);
    }
}
