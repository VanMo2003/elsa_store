package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.StoreMemberRequest;
import com.example.elsa_store.dto.request.StoreMemberUpdateRequest;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.UserMapper;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.StoreMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StoreMemberServiceImpl implements StoreMemberService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserAccountFactory accountFactory;
    private final AccessGuard accessGuard;

    @Override
    public UserResponse create(StoreMemberRequest req) {
        Store store = accessGuard.requireStoreManager();
        User current = accessGuard.currentUser();
        Role role = req.getRole();
        // Chủ tạo quản lý và nhân viên; quản lý chỉ tạo nhân viên.
        if (role == Role.STORE_MANAGER) {
            if (current.getRole() != Role.STORE_OWNER) {
                throw new AppException(ErrorCode.UN_AUTHORIZED, "Only the store owner can create managers");
            }
        } else if (role != Role.STAFF) {
            throw new AppException(ErrorCode.BAD_REQUEST, "role must be STORE_MANAGER or STAFF");
        }
        User member = accountFactory.create(
                req.getUsername(), req.getPassword(), role, req.getFullName(), req.getEmail(), req.getPhone(), store);
        return UserMapper.toResponse(member, store);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        Store store = accessGuard.requireStoreManager();
        return userRepository.findAllByStore_Id(store.getId()).stream()
                .sorted(Comparator.comparing(User::getRole).thenComparing(User::getId))
                .map(member -> UserMapper.toResponse(member, store))
                .toList();
    }

    @Override
    public UserResponse update(Long id, StoreMemberUpdateRequest req) {
        Store store = accessGuard.requireStoreManager();
        User member = manageable(id, store);
        if (req.getFullName() != null) {
            member.setFullName(req.getFullName().isBlank() ? null : req.getFullName().trim());
        }
        if (req.getEmail() != null) {
            String email = req.getEmail().isBlank() ? null : req.getEmail().trim();
            if (email != null && !email.equalsIgnoreCase(member.getEmail()) && userRepository.existsByEmail(email)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Account existed with email: " + email);
            }
            member.setEmail(email);
        }
        if (req.getPhone() != null) {
            member.setPhone(req.getPhone().isBlank() ? null : req.getPhone().trim());
        }
        if (req.getEnabled() != null) {
            if (member.isEnabled() && !req.getEnabled()) {
                // Khoá là đăng xuất ngay mọi thiết bị của nhân sự đó.
                member.markCredentialsChanged();
            }
            member.setEnabled(req.getEnabled());
        }
        return UserMapper.toResponse(userRepository.save(member), store);
    }

    @Override
    public void delete(Long id) {
        Store store = accessGuard.requireStoreManager();
        User member = manageable(id, store);
        if (orderRepository.existsByHandledBy_Id(id) || orderRepository.existsByUser_Id(id)) {
            member.setEnabled(false);
            member.markCredentialsChanged();
            userRepository.save(member);
            return;
        }
        notificationRepository.deleteAllByUserId(id);
        deviceTokenRepository.deleteAllByUserId(id);
        userRepository.delete(member);
    }

    /** Thành viên cùng cửa hàng mà người gọi được quản lý: chủ quản lý mọi người, quản lý chỉ quản lý nhân viên. */
    private User manageable(Long id, Store store) {
        User member = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found id=" + id));
        boolean sameStore = member.getStore() != null && member.getStore().getId().equals(store.getId());
        if (!sameStore || member.getRole() == Role.STORE_OWNER) {
            throw new ResourceNotFoundException("Member not found id=" + id);
        }
        User current = accessGuard.currentUser();
        if (member.getRole() == Role.STORE_MANAGER && current.getRole() != Role.STORE_OWNER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only the store owner can manage managers");
        }
        return member;
    }
}
