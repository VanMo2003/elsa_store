package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ShippingSettingsRequest;
import com.example.elsa_store.dto.request.StorePauseRequest;
import com.example.elsa_store.dto.request.StoreUpdateRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.StoreMapper;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.example.elsa_store.repository.StoreFollowRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final AccessGuard accessGuard;
    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final FileStorageService fileStorageService;
    private final StoreFollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> getAllPublic(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from(storeRepository.findAllApproved(pageable).map(StoreMapper::toPublicResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getPublic(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id=" + id));
        if (!store.isOpenToCustomers()) {
            throw new ResourceNotFoundException("Store not found with id=" + id);
        }
        StoreResponse response = StoreMapper.toPublicResponse(store);
        List<Object[]> rating = reviewRepository.storeStats(id);
        Object[] row = rating.isEmpty() ? new Object[] {null, 0L} : rating.get(0);
        long count = row[1] instanceof Number n ? n.longValue() : 0;
        response.setRatingCount(count);
        response.setRatingAverage(count == 0 || !(row[0] instanceof Number avg)
                ? null
                : Math.round(avg.doubleValue() * 10) / 10.0);
        response.setProductCount(productRepository.countOnSale(id));
        response.setSoldCount(orderItemRepository.soldQuantity(id));
        response.setFollowerCount(followRepository.countByStore_Id(id));
        accessGuard.currentUserOrEmpty()
                .filter(u -> u.getRole() == Role.USER)
                .ifPresent(u -> response.setFollowing(followRepository.existsByUser_IdAndStore_Id(u.getId(), id)));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getMine() {
        User current = accessGuard.currentUser();
        Store store = accessGuard.storeOf(current);
        if (store == null) {
            throw new AppException(ErrorCode.STORE_REQUIRED);
        }
        return StoreMapper.toResponse(store);
    }

    @Override
    public StoreResponse updateMine(StoreUpdateRequest req) {
        Store store = accessGuard.requireStoreManager();
        store.setName(req.getName().trim());
        if (req.getDescription() != null) {
            store.setDescription(req.getDescription().isBlank() ? null : req.getDescription().trim());
        }
        if (req.getImageUrl() != null) {
            store.setImageUrl(req.getImageUrl().isBlank() ? null : req.getImageUrl().trim());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            store.setEmail(req.getEmail().trim());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            store.setPhone(req.getPhone().trim());
        }
        if (req.getAddress() != null && !req.getAddress().isBlank()) {
            store.setAddress(req.getAddress().trim());
        }
        return StoreMapper.toResponse(storeRepository.save(store));
    }

    @Override
    public StoreResponse updatePause(StorePauseRequest req) {
        Store store = accessGuard.requireStoreManager();
        boolean paused = Boolean.TRUE.equals(req.getPaused());
        store.setPaused(paused ? Boolean.TRUE : null);
        String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();
        store.setPauseNote(paused ? note : null);
        return StoreMapper.toResponse(storeRepository.save(store));
    }

    @Override
    public StoreResponse updateImage(MultipartFile file) {
        Store store = accessGuard.requireStoreManager();
        store.setImageUrl(fileStorageService.storeStoreImage(file));
        return StoreMapper.toResponse(storeRepository.save(store));
    }

    /** File cũ giữ lại trên đĩa: tin nhắn chat đã gửi còn trỏ tới ảnh cửa hàng lúc đó. */
    @Override
    public StoreResponse removeImage() {
        Store store = accessGuard.requireStoreManager();
        store.setImageUrl(null);
        return StoreMapper.toResponse(storeRepository.save(store));
    }

    @Override
    public StoreResponse updateShipping(ShippingSettingsRequest req) {
        Store store = accessGuard.requireStoreManager();
        Double fee = req.getShippingFee() == null || req.getShippingFee() <= 0 ? null : (double) Math.round(req.getShippingFee());
        Double threshold = req.getFreeShippingThreshold() == null ? null : (double) Math.round(req.getFreeShippingThreshold());
        store.setShippingFee(fee);
        // Giao miễn phí sẵn thì mức miễn phí không còn ý nghĩa.
        store.setFreeShippingThreshold(fee == null ? null : threshold);
        return StoreMapper.toResponse(storeRepository.save(store));
    }
}
