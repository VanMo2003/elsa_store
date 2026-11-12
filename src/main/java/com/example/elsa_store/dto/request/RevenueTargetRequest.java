package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** Mục tiêu doanh thu tháng của cửa hàng; trống hoặc 0 = bỏ mục tiêu. */
public record RevenueTargetRequest(@DecimalMin("0") @DecimalMax("100000000000") Double target) {}
