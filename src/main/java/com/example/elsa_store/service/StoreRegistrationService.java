package com.example.elsa_store.service;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.request.StoreRegistrationRequest;
import com.example.elsa_store.dto.request.StoreRejectRequest;
import com.example.elsa_store.dto.request.StoreResubmitRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;

/** Đăng ký mở cửa hàng và luồng xét duyệt của ADMIN. */
public interface StoreRegistrationService {
    /** Công khai: tạo tài khoản STORE_OWNER cùng cửa hàng ở trạng thái chờ duyệt. */
    StoreResponse register(StoreRegistrationRequest req);

    /** Hồ sơ của chủ đang đăng nhập, ở mọi trạng thái duyệt. */
    StoreResponse getMine();

    StoreResponse resubmit(StoreResubmitRequest req);

    /** Hàng chờ duyệt (ADMIN): chờ lâu nhất trước; đã xử lý thì mới nhất trước. */
    PageResponse<StoreResponse> getAll(ApprovalStatus status, int page, int size);

    StoreResponse getById(Long id);

    StoreResponse approve(Long id);

    StoreResponse reject(Long id, StoreRejectRequest req);

    /** ADMIN đình chỉ cửa hàng đã duyệt (vi phạm) kèm lý do; báo chủ và quản lý. */
    StoreResponse suspend(Long id, StoreRejectRequest req);

    /** ADMIN mở lại cửa hàng bị đình chỉ; gọi lại khi đang mở thì không đổi gì. */
    StoreResponse reinstate(Long id);
}
