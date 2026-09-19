
package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Token FCM của thiết bị đang đăng nhập. Một token thuộc về người đăng nhập sau cùng trên thiết bị đó. */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "token", length = 512, nullable = false, unique = true)
    String token;

    @Column(name = "platform", length = 20)
    String platform;
}
