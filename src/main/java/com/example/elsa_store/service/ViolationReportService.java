package com.example.elsa_store.service;

import com.example.elsa_store.constant.ViolationStatus;
import com.example.elsa_store.dto.request.ViolationHandleRequest;
import com.example.elsa_store.dto.request.ViolationReportRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ViolationReportResponse;

/** Khách báo cáo sản phẩm vi phạm; ADMIN xem, xử lý hoặc bỏ qua. */
public interface ViolationReportService {

    ViolationReportResponse report(Long productId, ViolationReportRequest request);

    PageResponse<ViolationReportResponse> list(ViolationStatus status, int page, int size);

    ViolationReportResponse handle(Long id, ViolationHandleRequest request);
}
