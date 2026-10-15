package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.Role;
import lombok.*;

/** Hiệu suất người lập đơn tại quầy (chủ, quản lý, nhân viên) trong một khoảng thời gian. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPerformanceResponse {
    private Long userId;
    private String name;
    private Role role;
    private Long ordersCount;
    private Double revenue;
}
