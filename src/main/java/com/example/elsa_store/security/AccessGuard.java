package com.example.elsa_store.security;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Nơi duy nhất kiểm tra "tài khoản hiện tại có được đụng vào dữ liệu này không".
 * Quyền theo cửa hàng cần truy vấn DB (chủ → cửa hàng, quản lý/nhân viên → cửa hàng của họ) nên nằm ở đây
 * và được gọi từ tầng service, không dùng SpEL trên từng controller.
 *
 * <p>Ba mức truy cập vào dữ liệu của một cửa hàng ({@link Access}):
 * <ul>
 *   <li>{@code READ} — ADMIN (chỉ xem) và mọi vai trò của chính cửa hàng đó;</li>
 *   <li>{@code MANAGER_READ} — như READ nhưng nhân viên không xem được;</li>
 *   <li>{@code STAFF_WRITE} — tạo/sửa: chủ, quản lý, nhân viên; ADMIN bị từ chối ({@code ADMIN_READ_ONLY});</li>
 *   <li>{@code MANAGER_WRITE} — xoá, nhà cung cấp, khuyến mãi, nhân sự: chỉ chủ và quản lý.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    public enum Access {
        READ,
        STAFF_WRITE,
        /** Xem dữ liệu chỉ dành cho chủ / quản lý (nhà cung cấp, khuyến mãi, báo cáo); ADMIN được xem. */
        MANAGER_READ,
        MANAGER_WRITE
    }

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    // ------------------------------------------------------------------ tài khoản hiện tại

    /** Tài khoản đang đăng nhập, hoặc rỗng nếu là khách (endpoint công khai). */
    public Optional<User> currentUserOrEmpty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            return Optional.empty();
        }
        // Tài khoản bị khoá thì token đang cầm cũng hết tác dụng.
        return userRepository.findByUsername(authentication.getName()).filter(User::isEnabled);
    }

    public User currentUser() {
        return currentUserOrEmpty().orElseThrow(() -> new AppException(ErrorCode.UN_AUTHENTICATED));
    }

    public User requireAdmin() {
        User current = currentUser();
        if (current.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return current;
    }

    public User requireRole(Role... roles) {
        User current = currentUser();
        for (Role role : roles) {
            if (current.getRole() == role) {
                return current;
            }
        }
        throw new AppException(ErrorCode.UN_AUTHORIZED);
    }

    public void requireSelfOrAdmin(Long userId) {
        User current = currentUser();
        if (current.getRole() != Role.ADMIN && !current.getId().equals(userId)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
    }

    public void rejectAdmin(User current) {
        if (current.getRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.ADMIN_READ_ONLY);
        }
    }

    // ------------------------------------------------------------------ cửa hàng của tài khoản

    /** Cửa hàng của chủ / quản lý / nhân viên, ở mọi trạng thái duyệt; null với vai trò khác. */
    public Store storeOf(User user) {
        return switch (user.getRole()) {
            case STORE_OWNER -> storeRepository.findByOwner_Id(user.getId()).orElse(null);
            case STORE_MANAGER, STAFF -> user.getStore();
            default -> null;
        };
    }

    /** Chủ cửa hàng gọi các API đăng ký: thấy được cửa hàng của mình dù đang chờ duyệt hay bị từ chối. */
    public Store requireOwnStore() {
        User current = requireRole(Role.STORE_OWNER);
        Store store = storeRepository.findByOwner_Id(current.getId()).orElse(null);
        if (store == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Store not found for this account");
        }
        return store;
    }

    /** Cửa hàng của chủ đã được ADMIN duyệt. */
    public Store requireApprovedOwner() {
        Store store = requireOwnStore();
        if (!store.isApproved()) {
            throw new AppException(ErrorCode.STORE_NOT_APPROVED);
        }
        return store;
    }

    /** Cửa hàng đã duyệt của chủ / quản lý / nhân viên đang đăng nhập (ADMIN không có cửa hàng riêng). */
    public Store requireStoreMember() {
        User current = currentUser();
        rejectAdmin(current);
        if (!current.getRole().isStoreRole()) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return approvedStoreOf(current);
    }

    /** Như {@link #requireStoreMember()} nhưng chỉ chủ và quản lý. */
    public Store requireStoreManager() {
        User current = currentUser();
        rejectAdmin(current);
        if (!current.getRole().isStoreManager()) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return approvedStoreOf(current);
    }

    private Store approvedStoreOf(User current) {
        Store store = storeOf(current);
        if (store == null) {
            throw new AppException(ErrorCode.STORE_REQUIRED);
        }
        if (!store.isApproved()) {
            throw new AppException(ErrorCode.STORE_NOT_APPROVED);
        }
        return store;
    }

    // ------------------------------------------------------------------ dữ liệu của một cửa hàng

    /**
     * Cho phép tài khoản hiện tại truy cập dữ liệu thuộc {@code resourceStore} ở mức {@code access}.
     * Trả về tài khoản đang đăng nhập.
     */
    public User authorize(Store resourceStore, Access access) {
        return authorize(resourceStore == null ? null : resourceStore.getId(), access);
    }

    /** Tương tự {@link #authorize(Store, Access)} khi chỉ có id cửa hàng. */
    public User authorize(Long storeId, Access access) {
        User current = currentUser();
        if (current.getRole() == Role.ADMIN) {
            if (access != Access.READ && access != Access.MANAGER_READ) {
                throw new AppException(ErrorCode.ADMIN_READ_ONLY);
            }
            return current;
        }
        if (!current.getRole().isStoreRole()) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        Store mine = approvedStoreOf(current);
        if (storeId == null || !mine.getId().equals(storeId)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        boolean managerOnly = access == Access.MANAGER_READ || access == Access.MANAGER_WRITE;
        if (managerOnly && !current.getRole().isStoreManager()) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return current;
    }
}
