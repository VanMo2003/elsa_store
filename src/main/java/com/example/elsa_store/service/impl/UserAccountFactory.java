package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Nơi duy nhất tạo tài khoản đăng nhập, dùng chung cho khách tự đăng ký, chủ cửa hàng, quản lý và nhân viên. */
@Component
@RequiredArgsConstructor
public class UserAccountFactory {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    /** Tạo tài khoản; {@code store} chỉ áp dụng cho quản lý và nhân viên. Tài khoản USER có sẵn hồ sơ khách hàng. */
    public User create(
            String username, String password, Role role, String fullName, String email, String phone, Store store) {
        String name = username.trim();
        if (userRepository.existsByUsername(name)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Account existed with username: " + name);
        }
        String mail = blankToNull(email);
        if (mail != null && userRepository.existsByEmail(mail)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Account existed with email: " + mail);
        }
        User user = userRepository.save(User.builder()
                .username(name)
                .password(passwordEncoder.encode(password))
                .role(role)
                .fullName(blankToNull(fullName))
                .email(mail)
                .phone(blankToNull(phone))
                .store(role == Role.STORE_MANAGER || role == Role.STAFF ? store : null)
                .enabled(true)
                .build());
        if (role == Role.USER) {
            ensureCustomerProfile(user);
        }
        return user;
    }

    /** Hồ sơ khách hàng gắn với tài khoản USER (tạo nếu chưa có, ví dụ tài khoản cũ). */
    public Customer ensureCustomerProfile(User user) {
        return customerRepository
                .findByUser_Id(user.getId())
                .orElseGet(() -> customerRepository.save(Customer.builder().user(user).build()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
