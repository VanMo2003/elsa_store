
package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ApprovalStatus;
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
public class StoreResponse {
    Long id;
    String name;
    String description;
    String imageUrl;
    String ownerName;
    String email;
    String phone;
    String address;
    ApprovalStatus approvalStatus;
    String rejectionReason;
    LocalDateTime submittedAt;
    LocalDateTime reviewedAt;
    Long ownerId;
    String ownerUsername;
}
