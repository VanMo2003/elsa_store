package com.example.elsa_store.dto.response;

import lombok.*;

import java.util.List;

/** Các nhóm thông báo tài khoản này tắt được, theo thứ tự hiển thị. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsResponse {

    private List<Group> groups;

    public record Group(String group, boolean enabled) {}
}
