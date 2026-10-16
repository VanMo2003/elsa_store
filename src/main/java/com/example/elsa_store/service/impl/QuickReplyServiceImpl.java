package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.QuickReplyRequest;
import com.example.elsa_store.dto.response.QuickReplyResponse;
import com.example.elsa_store.entity.QuickReply;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.QuickReplyRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.QuickReplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class QuickReplyServiceImpl implements QuickReplyService {

    private final QuickReplyRepository repository;
    private final AccessGuard accessGuard;

    public QuickReplyServiceImpl(QuickReplyRepository repository, AccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuickReplyResponse> list() {
        Store store = accessGuard.requireStoreMember();
        return repository.findByStore_IdOrderByIdAsc(store.getId()).stream().map(QuickReplyServiceImpl::toResponse).toList();
    }

    @Override
    public QuickReplyResponse create(QuickReplyRequest request) {
        Store store = accessGuard.requireStoreMember();
        if (repository.countByStore_Id(store.getId()) >= QuickReply.MAX_PER_STORE) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Too many quick replies (max " + QuickReply.MAX_PER_STORE + ")");
        }
        QuickReply reply = QuickReply.builder().store(store).build();
        apply(reply, request);
        return toResponse(repository.save(reply));
    }

    @Override
    public QuickReplyResponse update(Long id, QuickReplyRequest request) {
        QuickReply reply = mine(id);
        apply(reply, request);
        return toResponse(repository.save(reply));
    }

    @Override
    public void delete(Long id) {
        repository.delete(mine(id));
    }

    /** Câu trả lời nhanh của chính cửa hàng; của cửa hàng khác coi như không có. */
    private QuickReply mine(Long id) {
        Store store = accessGuard.requireStoreMember();
        return repository.findById(id)
                .filter(r -> r.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Quick reply not found"));
    }

    private static void apply(QuickReply reply, QuickReplyRequest request) {
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (title.isEmpty() || content.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "title, content: must not be blank");
        }
        reply.setTitle(title);
        reply.setContent(content);
    }

    static QuickReplyResponse toResponse(QuickReply r) {
        return new QuickReplyResponse(r.getId(), r.getTitle(), r.getContent());
    }
}
