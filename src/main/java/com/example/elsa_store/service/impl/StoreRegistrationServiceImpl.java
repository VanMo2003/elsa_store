package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.StoreRegistrationRequest;
import com.example.elsa_store.dto.request.StoreRejectRequest;
import com.example.elsa_store.dto.request.StoreResubmitRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.StoreReviewedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.StoreMapper;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.StoreRegistrationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Chủ cửa hàng tự đăng ký. Tài khoản đăng nhập được ngay, nhưng mọi thao tác vận hành cửa hàng bị khoá
 * ({@link AccessGuard#requireApprovedOwner()}) cho tới khi ADMIN duyệt hồ sơ.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StoreRegistrationServiceImpl implements StoreRegistrationService {

    private final StoreRepository storeRepository;
    private final UserAccountFactory accountFactory;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public StoreResponse register(StoreRegistrationRequest req) {
        User owner = accountFactory.create(
                req.getUsername(),
                req.getPassword(),
                Role.STORE_OWNER,
                req.getOwnerName(),
                req.getEmail(),
                req.getPhone(),
                null);

        Store store = Store.builder().owner(owner).build();
        applyDetails(
                store,
                req.getStoreName(),
                req.getDescription(),
                req.getOwnerName(),
                req.getEmail(),
                req.getPhone(),
                req.getAddress());
        store.setApprovalStatus(ApprovalStatus.PENDING);
        store.setSubmittedAt(LocalDateTime.now());
        store = storeRepository.save(store);

        notifyAdmins(store, false);
        return StoreMapper.toResponse(store);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getMine() {
        return StoreMapper.toResponse(accessGuard.requireOwnStore());
    }

    @Override
    public StoreResponse resubmit(StoreResubmitRequest req) {
        Store store = accessGuard.requireOwnStore();
        if (store.isApproved()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Registration has already been approved");
        }
        applyDetails(
                store,
                req.getStoreName(),
                req.getDescription(),
                req.getOwnerName(),
                req.getEmail(),
                req.getPhone(),
                req.getAddress());
        boolean wasRejected = store.getApprovalStatus() == ApprovalStatus.REJECTED;
        store.setApprovalStatus(ApprovalStatus.PENDING);
        store.setSubmittedAt(LocalDateTime.now());
        store.setReviewedAt(null);
        store = storeRepository.save(store);

        // Sửa hồ sơ đang chờ duyệt thì không cần báo ADMIN lại.
        if (wasRejected) {
            notifyAdmins(store, true);
        }
        return StoreMapper.toResponse(store);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> getAll(ApprovalStatus status, int page, int size) {
        accessGuard.requireAdmin();
        Sort sort = status == ApprovalStatus.PENDING
                ? Sort.by(Sort.Direction.ASC, "submittedAt")
                : Sort.by(Sort.Direction.DESC, "reviewedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<Store> data = status == null
                ? storeRepository.findAll(pageable)
                : storeRepository.findAllByApprovalStatus(status, pageable);
        return PageResponse.from(data.map(StoreMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getById(Long id) {
        accessGuard.requireAdmin();
        return StoreMapper.toResponse(findStore(id));
    }

    @Override
    public StoreResponse approve(Long id) {
        accessGuard.requireAdmin();
        Store store = findPending(id);
        store.setApprovalStatus(ApprovalStatus.APPROVED);
        store.setRejectionReason(null);
        store.setReviewedAt(LocalDateTime.now());
        store = storeRepository.save(store);

        notificationService.notify(
                store.getOwner(),
                NotificationType.STORE_APPROVED,
                NotificationMessages.storeApproved(store),
                AppLinks.storeStatus(),
                store.getId());
        eventPublisher.publishEvent(StoreReviewedEvent.of(store));
        return StoreMapper.toResponse(store);
    }

    @Override
    public StoreResponse reject(Long id, StoreRejectRequest req) {
        accessGuard.requireAdmin();
        Store store = findPending(id);
        String reason = req.getReason().trim();
        store.setApprovalStatus(ApprovalStatus.REJECTED);
        store.setRejectionReason(reason);
        store.setReviewedAt(LocalDateTime.now());
        store = storeRepository.save(store);

        notificationService.notify(
                store.getOwner(),
                NotificationType.STORE_REJECTED,
                NotificationMessages.storeRejected(store, reason),
                AppLinks.storeStatus(),
                store.getId());
        eventPublisher.publishEvent(StoreReviewedEvent.of(store));
        return StoreMapper.toResponse(store);
    }

    private void notifyAdmins(Store store, boolean resubmitted) {
        notificationService.notifyAdmins(
                NotificationType.STORE_REGISTERED,
                NotificationMessages.storeSubmitted(store, resubmitted),
                AppLinks.storeRegistration(store.getId()),
                store.getId());
    }

    private Store findStore(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id=" + id));
    }

    private Store findPending(Long id) {
        Store store = findStore(id);
        if (store.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        return store;
    }

    private static void applyDetails(
            Store store,
            String storeName,
            String description,
            String ownerName,
            String email,
            String phone,
            String address) {
        store.setName(storeName.trim());
        store.setDescription(description == null || description.isBlank() ? null : description.trim());
        store.setOwnerName(ownerName.trim());
        store.setEmail(email.trim());
        store.setPhone(phone.trim());
        store.setAddress(address.trim());
    }
}
