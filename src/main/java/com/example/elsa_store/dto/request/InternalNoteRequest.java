package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Size;

/** Ghi chú nội bộ của cửa hàng cho một đơn (khách không thấy); trống = xoá ghi chú. */
public record InternalNoteRequest(@Size(max = 1000) String note) {}
