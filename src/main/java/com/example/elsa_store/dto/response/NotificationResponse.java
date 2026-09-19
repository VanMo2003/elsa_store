
package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    Long id;
    NotificationType type;
    String title;
    String body;
    String link;
    Long referenceId;
    boolean read;
    LocalDateTime createdAt;
}
