package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.QuickReplyRequest;
import com.example.elsa_store.dto.response.QuickReplyResponse;

import java.util.List;

/** Câu trả lời nhanh trong chat: chủ, quản lý, nhân viên của cửa hàng xem và quản lý chung một danh sách. */
public interface QuickReplyService {

    List<QuickReplyResponse> list();

    QuickReplyResponse create(QuickReplyRequest request);

    QuickReplyResponse update(Long id, QuickReplyRequest request);

    void delete(Long id);
}
