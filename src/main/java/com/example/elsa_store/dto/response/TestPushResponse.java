package com.example.elsa_store.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/** Push thử sẽ làm gì: không có gì nếu BE chưa bật FCM hoặc tài khoản chưa có thiết bị nào đăng ký. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TestPushResponse {
    boolean pushEnabled;
    int devices;
    int delaySeconds;
}
