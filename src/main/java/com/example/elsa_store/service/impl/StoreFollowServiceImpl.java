package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.StoreFollow;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.StoreMapper;
import com.example.elsa_store.repository.StoreFollowRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.StoreFollowService;
import com.example.elsa_store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StoreFollowServiceImpl implements StoreFollowService {

    private final StoreFollowRepository followRepository;
    private final StoreRepository storeRepository;
    private final AccessGuard accessGuard;
    private final StoreService storeService;

    @Override
    public StoreResponse follow(Long storeId) {
        User user = customer();
        Store store = openStore(storeId);
        if (!followRepository.existsByUser_IdAndStore_Id(user.getId(), store.getId())) {
            followRepository.save(StoreFollow.builder().user(user).store(store).build());
        }
        return storeService.getPublic(storeId);
    }

    @Override
    public StoreResponse unfollow(Long storeId) {
        User user = customer();
        followRepository.findByUser_IdAndStore_Id(user.getId(), storeId).ifPresent(followRepository::delete);
        followRepository.flush();
        return storeService.getPublic(storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> following(int page, int size) {
        User user = customer();
        var result = followRepository.findByUser_IdOrderByIdDesc(user.getId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.from(result.map(f -> {
            StoreResponse res = StoreMapper.toPublicResponse(f.getStore());
            res.setFollowing(true);
            return res;
        }));
    }

    private User customer() {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only customers can follow stores");
        }
        return user;
    }

    private Store openStore(Long storeId) {
        return storeRepository.findById(storeId)
                .filter(Store::isApproved)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id=" + storeId));
    }
}
