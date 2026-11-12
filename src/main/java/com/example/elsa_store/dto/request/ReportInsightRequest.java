package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Kỳ báo cáo cần AI nhận xét (tính cả hai đầu); {@code storeId} chỉ ADMIN dùng, bỏ trống = toàn sàn / cửa hàng của mình. */
public record ReportInsightRequest(@NotNull LocalDate from, @NotNull LocalDate to, Long storeId) {}
