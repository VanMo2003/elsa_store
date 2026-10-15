package com.example.elsa_store.service;

import com.example.elsa_store.constant.ReturnStatus;
import com.example.elsa_store.dto.request.ReturnCreateRequest;
import com.example.elsa_store.dto.request.ReturnDecisionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReturnResponse;

/** Đổi / trả hàng: khách gửi yêu cầu cho đơn đã hoàn thành, cửa hàng duyệt / từ chối / hoàn tiền. */
public interface ReturnService {

    ReturnResponse create(ReturnCreateRequest request);

    /** Khách huỷ yêu cầu còn chờ duyệt. */
    ReturnResponse cancel(Long id);

    ReturnResponse approve(Long id, ReturnDecisionRequest request);

    ReturnResponse reject(Long id, ReturnDecisionRequest request);

    /** Đã nhận lại hàng và hoàn tiền (chủ / quản lý): hàng về kho, tiền hoàn trừ vào doanh thu ngày hoàn. */
    ReturnResponse refund(Long id, ReturnDecisionRequest request);

    ReturnResponse get(Long id);

    /** Yêu cầu của khách đang đăng nhập; {@code orderId} để xem yêu cầu của một đơn. */
    PageResponse<ReturnResponse> mine(Long orderId, int page, int size);

    /** Yêu cầu gửi tới cửa hàng của người đang đăng nhập (ADMIN: mọi cửa hàng hoặc {@code storeId}). */
    PageResponse<ReturnResponse> forStore(ReturnStatus status, Long storeId, int page, int size);
}
