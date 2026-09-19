package com.example.elsa_store.event;

import java.util.List;
import java.util.Map;

/** Phát ra khi thông báo đã được lưu; được đẩy tới thiết bị của người nhận sau khi transaction commit. */
public record PushRequestedEvent(List<Long> userIds, String title, String body, Map<String, String> data) {}
