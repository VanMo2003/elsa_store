
package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.response.NotificationResponse;
import com.example.elsa_store.entity.Notification;

public class NotificationMapper {

    public static NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
