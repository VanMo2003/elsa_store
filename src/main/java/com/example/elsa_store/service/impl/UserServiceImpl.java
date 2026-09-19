
package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.UserMapper;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;


    @Override
    public UserResponse create(UserRequest req) {
        boolean checkExist = userRepository.existsByUsernameOrEmail(req.getUsername(), req.getEmail());
        if (checkExist) {
            throw new ResourceNotFoundException("Account existed with username or email");
        }
        User entity = UserMapper.toEntity(req);
        entity.setPassword(passwordEncoder.encode(req.getPassword()));
        userRepository.save(entity);
        return UserMapper.toResponse(entity);
    }

    @Override
    public UserResponse update(Long id, UserRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserMapper.update(u, req);
        return UserMapper.toResponse(u);
    }

    @Override
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        // Thông báo và token thiết bị tham chiếu tới user nên phải xoá trước.
        notificationRepository.deleteAllByUserId(id);
        deviceTokenRepository.deleteAllByUserId(id);
        userRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserMapper.toResponse(u);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }
}
