
package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ChangePasswordRequest;
import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.request.UserUpdateRequest;
import com.example.elsa_store.dto.response.AuthenticationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.UserMapper;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final WishlistItemRepository wishlistRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final CustomerRepository customerRepository;
    private final UserAccountFactory accountFactory;
    private final AccessGuard accessGuard;
    private final FileStorageService fileStorageService;
    private final AuthenticationService authenticationService;

    @Override
    public UserResponse create(UserRequest req) {
        Role requested = req.getRole() == null ? Role.USER : req.getRole();
        // Khách tự đăng ký; chủ cửa hàng đăng ký qua /store-registrations, quản lý và nhân viên do chủ / quản lý tạo
        // qua /store-members, còn ADMIN chỉ có tài khoản được tạo sẵn.
        switch (requested) {
            case USER -> {
                // đăng ký công khai, không cần người gọi đã đăng nhập
            }
            case STORE_OWNER -> throw new AppException(
                    ErrorCode.BAD_REQUEST, "Store owner accounts must register via POST /store-registrations");
            case STORE_MANAGER, STAFF -> throw new AppException(
                    ErrorCode.BAD_REQUEST, "Store manager and staff accounts must be created via POST /store-members");
            case ADMIN -> throw new AppException(ErrorCode.UN_AUTHORIZED, "Admin accounts cannot be created");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "password: must not be blank");
        }
        User user = accountFactory.create(
                req.getUsername(), req.getPassword(), Role.USER, req.getFullName(), req.getEmail(), req.getPhone(), null);
        return UserMapper.toResponse(user);
    }

    @Override
    public UserResponse update(Long id, UserUpdateRequest req) {
        User current = accessGuard.currentUser();
        boolean isAdmin = current.getRole() == Role.ADMIN;
        boolean isSelf = current.getId().equals(id);
        if (!isAdmin && !isSelf) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        // ADMIN chỉ khoá / mở tài khoản; hồ sơ và mật khẩu là của chính chủ.
        if (!isSelf && (req.getPassword() != null || req.getFullName() != null
                || req.getEmail() != null || req.getPhone() != null)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only the account holder can change its profile");
        }
        if (!isAdmin && req.getEnabled() != null) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only ADMIN can lock or unlock accounts");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (isAdmin && !isSelf && user.getRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Admin accounts cannot be locked");
        }
        if (req.getFullName() != null) {
            user.setFullName(req.getFullName());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail().isBlank() ? null : req.getEmail());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getPassword() != null) {
            requireCurrentPassword(user, req.getCurrentPassword());
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            user.markCredentialsChanged();
        }
        if (req.getEnabled() != null) {
            if (user.isEnabled() && !req.getEnabled()) {
                // Khoá là đăng xuất ngay mọi thiết bị của tài khoản đó (token cũ bị từ chối kể cả khi mở khoá lại).
                user.markCredentialsChanged();
            }
            user.setEnabled(req.getEnabled());
        }
        return UserMapper.toResponse(userRepository.save(user), accessGuard.storeOf(user));
    }

    @Override
    public AuthenticationResponse changePassword(ChangePasswordRequest req) {
        User user = accessGuard.currentUser();
        requireCurrentPassword(user, req.getCurrentPassword());
        if (req.getNewPassword().equals(req.getCurrentPassword())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "New password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        // Các thiết bị khác bị đăng xuất; thiết bị đang đổi nhận cặp token mới ngay bên dưới.
        user.markCredentialsChanged();
        userRepository.save(user);
        return authenticationService.issueTokens(user);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AppException(ErrorCode.INCORRECT_CURRENT_PASSWORD);
        }
    }

    @Override
    public void delete(Long id) {
        accessGuard.requireAdmin();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Chủ, quản lý, nhân viên gắn với cửa hàng và đơn hàng — khoá tài khoản thay vì xoá.
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only customer accounts can be deleted; lock the account instead");
        }
        // Thông báo và token thiết bị tham chiếu tới user nên phải xoá trước.
        notificationRepository.deleteAllByUserId(id);
        deviceTokenRepository.deleteAllByUserId(id);
        wishlistRepository.deleteAllByUserId(id);
        customerRepository.findByUser_Id(id).ifPresent(customer -> {
            customer.setUser(null);
            customerRepository.save(customer);
        });
        userRepository.delete(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        accessGuard.requireSelfOrAdmin(id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        return toResponse(accessGuard.currentUser());
    }

    @Override
    public UserResponse updateAvatar(MultipartFile file) {
        User user = accessGuard.currentUser();
        user.setAvatarUrl(fileStorageService.storeAvatar(file));
        return toResponse(userRepository.save(user));
    }

    /** File cũ giữ lại trên đĩa (đánh giá / tin nhắn đã tải về máy khác có thể còn trỏ tới). */
    @Override
    public UserResponse removeAvatar() {
        User user = accessGuard.currentUser();
        user.setAvatarUrl(null);
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAll(Role role, String keyword, int page, int size) {
        accessGuard.requireAdmin();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> data = userRepository.search(role, OrderServiceImpl.likePattern(keyword), pageable);
        return PageResponse.from(data.map(this::toResponse));
    }

    private UserResponse toResponse(User user) {
        Store store = accessGuard.storeOf(user);
        return UserMapper.toResponse(user, store);
    }
}
