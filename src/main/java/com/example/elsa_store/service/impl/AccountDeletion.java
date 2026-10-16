package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.DeleteAccountRequest;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CartRepository;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.StoreFollowRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Khách tự xoá tài khoản (yêu cầu của kho ứng dụng): xác nhận bằng mật khẩu, chỉ khi không còn đơn đang xử lý. Đơn hàng,
 * đánh giá, sổ xu được giữ cho sổ sách cửa hàng nhưng mọi thông tin cá nhân bị xoá / ẩn danh: tên đăng nhập đổi thành
 * {@code deleted_<id>_…}, email / SĐT / ảnh đại diện bỏ, hồ sơ khách và sổ địa chỉ ẩn danh; giỏ hàng, yêu thích, theo dõi
 * cửa hàng, thông báo, thiết bị nhận push bị xoá. Tài khoản bị khoá và mọi token hết hiệu lực ngay.
 */
@Service
@Transactional
public class AccountDeletion {

    static final String DELETED_NAME = "Tài khoản đã xoá";
    private static final EnumSet<OrderStatus> IN_PROGRESS =
            EnumSet.of(OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_XAC_NHAN, OrderStatus.DANG_GIAO);

    private final AccessGuard accessGuard;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final WishlistItemRepository wishlistRepository;
    private final StoreFollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public AccountDeletion(AccessGuard accessGuard,
                           PasswordEncoder passwordEncoder,
                           UserRepository userRepository,
                           OrderRepository orderRepository,
                           CustomerRepository customerRepository,
                           CartRepository cartRepository,
                           WishlistItemRepository wishlistRepository,
                           StoreFollowRepository followRepository,
                           NotificationRepository notificationRepository,
                           DeviceTokenRepository deviceTokenRepository) {
        this.accessGuard = accessGuard;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
        this.wishlistRepository = wishlistRepository;
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    public void deleteMe(DeleteAccountRequest request) {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Store and admin accounts cannot be deleted here");
        }
        if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INCORRECT_CURRENT_PASSWORD);
        }
        if (orderRepository.countByUser_IdAndStatusIn(user.getId(), IN_PROGRESS) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "You still have orders in progress");
        }
        Long id = user.getId();
        deviceTokenRepository.deleteAllByUserId(id);
        notificationRepository.deleteAllByUserId(id);
        wishlistRepository.deleteAllByUserId(id);
        followRepository.deleteAll(followRepository.findAllByUser_Id(id));
        cartRepository.findByUserId(id).ifPresent(cartRepository::delete);
        customerRepository.findByUser_Id(id).ifPresent(customer -> {
            customer.setFullName(DELETED_NAME);
            customer.setPhone(null);
            customer.setEmail(null);
            customer.setDob(null);
            if (customer.getAddresses() != null) {
                customer.getAddresses().forEach(address -> {
                    address.setFullName(DELETED_NAME);
                    address.setPhone(null);
                    address.setDetail(null);
                    address.setWard(null);
                    address.setIsDefault(false);
                });
            }
            customerRepository.save(customer);
        });

        user.setUsername("deleted_" + id + "_" + UUID.randomUUID().toString().substring(0, 6));
        user.setEmail(null);
        user.setPhone(null);
        user.setFullName(DELETED_NAME);
        user.setAvatarUrl(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.markCredentialsChanged();
        userRepository.save(user);
    }
}
