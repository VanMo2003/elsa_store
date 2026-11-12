package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nhập mã giới thiệu sau khi đã đăng ký (trong 7 ngày đầu, khi chưa có đơn hoàn thành). */
public record ReferralApplyRequest(@NotBlank @Size(max = 20) String code) {}
