package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.BannerLinkType;
import com.example.elsa_store.dto.request.BannerRequest;
import com.example.elsa_store.dto.response.BannerResponse;
import com.example.elsa_store.entity.HomeBanner;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.HomeBannerRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.HomeBannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class HomeBannerServiceImpl implements HomeBannerService {

    private final HomeBannerRepository repository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final AccessGuard accessGuard;
    private final Clock clock;

    @Autowired
    public HomeBannerServiceImpl(HomeBannerRepository repository,
                                 ProductRepository productRepository,
                                 StoreRepository storeRepository,
                                 CategoryRepository categoryRepository,
                                 FileStorageService fileStorageService,
                                 AccessGuard accessGuard) {
        this(repository, productRepository, storeRepository, categoryRepository, fileStorageService, accessGuard,
                Clock.systemDefaultZone());
    }

    HomeBannerServiceImpl(HomeBannerRepository repository,
                          ProductRepository productRepository,
                          StoreRepository storeRepository,
                          CategoryRepository categoryRepository,
                          FileStorageService fileStorageService,
                          AccessGuard accessGuard,
                          Clock clock) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> showing() {
        return repository.findShowing(LocalDateTime.now(clock)).stream().map(HomeBannerServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> all() {
        accessGuard.requireAdmin();
        return repository.findAllOrdered().stream().map(HomeBannerServiceImpl::toResponse).toList();
    }

    @Override
    public BannerResponse create(BannerRequest request) {
        accessGuard.requireAdmin();
        if (repository.count() >= HomeBanner.MAX_BANNERS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Too many banners (max " + HomeBanner.MAX_BANNERS + ")");
        }
        HomeBanner banner = new HomeBanner();
        apply(banner, request);
        return toResponse(repository.save(banner));
    }

    @Override
    public BannerResponse update(Long id, BannerRequest request) {
        accessGuard.requireAdmin();
        HomeBanner banner = find(id);
        apply(banner, request);
        return toResponse(repository.save(banner));
    }

    @Override
    public BannerResponse updateImage(Long id, MultipartFile file) {
        accessGuard.requireAdmin();
        HomeBanner banner = find(id);
        banner.setImageUrl(fileStorageService.storeBannerImage(file));
        return toResponse(repository.save(banner));
    }

    @Override
    public void delete(Long id) {
        accessGuard.requireAdmin();
        repository.delete(find(id));
    }

    private HomeBanner find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
    }

    /** Kiểm liên kết: id sản phẩm / cửa hàng / danh mục phải tồn tại, từ khoá không trống; thời gian kết thúc sau bắt đầu. */
    private void apply(HomeBanner banner, BannerRequest request) {
        BannerLinkType type = request.getLinkType() == null ? BannerLinkType.NONE : request.getLinkType();
        String value = request.getLinkValue() == null ? null : request.getLinkValue().trim();
        switch (type) {
            case NONE -> value = null;
            case SEARCH -> {
                if (value == null || value.isEmpty()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "linkValue: search keyword is required");
                }
            }
            case PRODUCT, STORE, CATEGORY -> {
                long target = parseId(value);
                boolean exists = switch (type) {
                    case PRODUCT -> productRepository.existsById(target);
                    case STORE -> storeRepository.existsById(target);
                    default -> categoryRepository.existsById(target);
                };
                if (!exists) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "linkValue: " + type.name().toLowerCase() + " not found");
                }
                value = String.valueOf(target);
            }
        }
        if (request.getStartsAt() != null && request.getEndsAt() != null
                && !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "endsAt must be after startsAt");
        }
        banner.setTitle(request.getTitle().trim());
        String subtitle = request.getSubtitle() == null ? null : request.getSubtitle().trim();
        banner.setSubtitle(subtitle == null || subtitle.isEmpty() ? null : subtitle);
        banner.setLinkType(type);
        banner.setLinkValue(value);
        banner.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        banner.setActive(request.getActive() == null || request.getActive());
        banner.setStartsAt(request.getStartsAt());
        banner.setEndsAt(request.getEndsAt());
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value == null ? "" : value);
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "linkValue: must be an id");
        }
    }

    static BannerResponse toResponse(HomeBanner b) {
        return new BannerResponse(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(), b.getLinkType(),
                b.getLinkValue(), b.getSortOrder() == null ? 0 : b.getSortOrder(), Boolean.TRUE.equals(b.getActive()),
                b.getStartsAt(), b.getEndsAt());
    }
}
