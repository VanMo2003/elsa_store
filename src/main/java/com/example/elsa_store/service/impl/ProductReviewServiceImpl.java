package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ReviewReplyRequest;
import com.example.elsa_store.dto.request.ReviewRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReviewEligibilityResponse;
import com.example.elsa_store.dto.response.ReviewResponse;
import com.example.elsa_store.dto.response.ReviewSummaryResponse;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductReview;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.ProductReviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final AccessGuard accessGuard;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public ProductReviewServiceImpl(ProductReviewRepository reviewRepository,
                                    ProductRepository productRepository,
                                    OrderItemRepository orderItemRepository,
                                    AccessGuard accessGuard,
                                    FileStorageService fileStorageService,
                                    NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.accessGuard = accessGuard;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ đọc (công khai)

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long productId, Integer rating, boolean withImages, int page, int size) {
        visibleProduct(productId);
        Long viewerId = accessGuard.currentUserOrEmpty().map(User::getId).orElse(null);
        var result = reviewRepository.findForProduct(productId, validRating(rating), withImages,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.from(result.map(r -> toResponse(r, viewerId)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        visibleProduct(productId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(String.valueOf(stars), 0L);
        }
        long count = 0;
        long total = 0;
        for (Object[] row : reviewRepository.distribution(productId)) {
            int stars = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            distribution.put(String.valueOf(stars), n);
            count += n;
            total += stars * n;
        }
        return ReviewSummaryResponse.builder()
                .average(count == 0 ? null : round1((double) total / count))
                .count(count)
                .distribution(distribution)
                .withImages(count == 0 ? 0L : reviewRepository.countWithImages(productId))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse eligibility(Long productId) {
        Product product = visibleProduct(productId);
        Optional<User> current = accessGuard.currentUserOrEmpty();
        if (current.isEmpty()) {
            return ReviewEligibilityResponse.builder().canReview(false).reason("NOT_SIGNED_IN").build();
        }
        User user = current.get();
        if (user.getRole() != Role.USER) {
            return ReviewEligibilityResponse.builder().canReview(false).reason("NOT_CUSTOMER").build();
        }
        ReviewResponse mine = reviewRepository.findByUser_IdAndProduct_Id(user.getId(), product.getId())
                .map(r -> toResponse(r, user.getId()))
                .orElse(null);
        boolean purchased = latestPurchase(user, product).isPresent();
        return ReviewEligibilityResponse.builder()
                .canReview(purchased)
                .reason(purchased ? null : "NOT_PURCHASED")
                .review(mine)
                .build();
    }

    // ------------------------------------------------------------------ khách viết / xoá

    @Override
    public ReviewResponse upsert(Long productId, ReviewRequest request) {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        Product product = visibleProduct(productId);
        OrderItem purchase = latestPurchase(user, product).orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_ALLOWED));
        int rating = Objects.requireNonNull(validRating(request.getRating()));

        ProductReview review = reviewRepository.findByUser_IdAndProduct_Id(user.getId(), product.getId()).orElse(null);
        boolean created = review == null;
        if (created) {
            review = new ProductReview();
            review.setUser(user);
            review.setProduct(product);
        } else {
            review.setEditedAt(LocalDateTime.now());
        }
        review.setRating(rating);
        review.setComment(trimToNull(request.getComment()));
        review.setVariantLabel(options(purchase.getProductVariant()));
        review.setOrderCode(purchase.getOrder().getCode());
        ProductReview saved = reviewRepository.save(review);
        // Chỉ báo khi có đánh giá mới; khách sửa lại thì không làm phiền cửa hàng thêm lần nữa.
        if (created && product.getStore() != null) {
            notificationService.notifyStore(product.getStore(), NotificationType.REVIEW_NEW,
                    NotificationMessages.reviewNew(saved), AppLinks.storeReviews(), saved.getId());
        }
        return toResponse(saved, user.getId());
    }

    @Override
    public ReviewResponse addImages(Long productId, List<MultipartFile> files) {
        User user = accessGuard.currentUser();
        ProductReview review = myReview(user, productId);
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "files: must not be empty");
        }
        if (review.getImages().size() + files.size() > ProductReview.MAX_IMAGES) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "A review can have at most " + ProductReview.MAX_IMAGES + " images");
        }
        files.stream().map(fileStorageService::storeReviewImage).forEach(review.getImages()::add);
        return toResponse(reviewRepository.save(review), user.getId());
    }

    @Override
    public ReviewResponse removeImage(Long productId, String url) {
        User user = accessGuard.currentUser();
        ProductReview review = myReview(user, productId);
        if (!review.getImages().remove(url)) {
            throw new ResourceNotFoundException("Image not found");
        }
        return toResponse(reviewRepository.save(review), user.getId());
    }

    /** Đánh giá của chính khách cho sản phẩm (chưa viết thì 404). */
    private ProductReview myReview(User user, Long productId) {
        return reviewRepository.findByUser_IdAndProduct_Id(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
    }

    @Override
    public void delete(Long reviewId) {
        ProductReview review = find(reviewId);
        User user = accessGuard.currentUser();
        if (!review.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        reviewRepository.delete(review);
    }

    // ------------------------------------------------------------------ phía cửa hàng

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> storeReviews(Integer rating, boolean unreplied, int page, int size) {
        Store store = accessGuard.requireStoreMember();
        var result = reviewRepository.findForStore(store.getId(), validRating(rating), unreplied,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.from(result.map(r -> toResponse(r, null)));
    }

    @Override
    public ReviewResponse reply(Long reviewId, ReviewReplyRequest request) {
        ProductReview review = find(reviewId);
        User staff = accessGuard.authorize(review.getProduct().getStore(), Access.STAFF_WRITE);
        String reply = trimToNull(request.getReply());
        if (reply == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "reply: must not be blank");
        }
        boolean changed = !reply.equals(review.getStoreReply());
        review.setStoreReply(reply);
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(staff);
        ProductReview saved = reviewRepository.save(review);
        if (changed) {
            notificationService.notify(saved.getUser(), NotificationType.REVIEW_REPLIED,
                    NotificationMessages.reviewReplied(saved), AppLinks.product(saved.getProduct().getId()),
                    saved.getProduct().getId());
        }
        return toResponse(saved, null);
    }

    // ------------------------------------------------------------------ nội bộ

    private Optional<OrderItem> latestPurchase(User user, Product product) {
        return orderItemRepository.completedPurchases(user.getId(), product.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /** Sản phẩm đang bán, hoặc người của chính cửa hàng / ADMIN xem cả sản phẩm đang ẩn — giống trang chi tiết. */
    private Product visibleProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (ProductServiceImpl.isPublic(product)) {
            return product;
        }
        User current = accessGuard.currentUserOrEmpty().orElse(null);
        if (current != null) {
            if (current.getRole() == Role.ADMIN) {
                return product;
            }
            Store mine = accessGuard.storeOf(current);
            if (mine != null && product.getStore() != null && mine.getId().equals(product.getStore().getId())) {
                return product;
            }
        }
        throw new ResourceNotFoundException("Product not found");
    }

    private ProductReview find(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review not found"));
    }

    private static Integer validRating(Integer rating) {
        if (rating == null) {
            return null;
        }
        if (rating < 1 || rating > 5) {
            throw new AppException(ErrorCode.BAD_REQUEST, "rating: must be between 1 and 5");
        }
        return rating;
    }

    static ReviewResponse toResponse(ProductReview r, Long viewerId) {
        Product p = r.getProduct();
        return ReviewResponse.builder()
                .id(r.getId())
                .productId(p.getId())
                .productName(p.getName())
                .productImageUrl(p.getImageUrl())
                .rating(r.getRating())
                .comment(r.getComment())
                .variantLabel(r.getVariantLabel())
                .authorName(maskedName(r.getUser().getUsername()))
                .createdAt(r.getCreatedAt())
                .edited(r.getEditedAt() != null)
                .storeReply(r.getStoreReply())
                .repliedAt(r.getRepliedAt())
                .mine(viewerId != null && viewerId.equals(r.getUser().getId()))
                .images(r.getImages() == null ? List.of() : List.copyOf(r.getImages()))
                .build();
    }

    /** Che tên đăng nhập: "khach001" → "k******1" — đủ để phân biệt mà không lộ tài khoản. */
    static String maskedName(String username) {
        if (username == null || username.isBlank()) {
            return "Khách hàng";
        }
        String name = username.trim();
        if (name.length() <= 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(Math.min(6, name.length() - 2)) + name.charAt(name.length() - 1);
    }

    static String options(ProductVariant v) {
        if (v == null) {
            return null;
        }
        String color = v.getColor() == null ? "" : v.getColor().trim();
        String size = v.getSize() == null ? "" : v.getSize().trim();
        if (color.isEmpty() && size.isEmpty()) {
            return null;
        }
        return color.isEmpty() ? size : size.isEmpty() ? color : color + " / " + size;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Điểm trung bình / số lượt của nhiều sản phẩm — cho danh sách sản phẩm. */
    static Map<Long, double[]> stats(ProductReviewRepository repository, List<Long> productIds) {
        Map<Long, double[]> result = new java.util.HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.stats(productIds)) {
            result.put(((Number) row[0]).longValue(),
                    new double[] {round1(((Number) row[1]).doubleValue()), ((Number) row[2]).doubleValue()});
        }
        return result;
    }
}
