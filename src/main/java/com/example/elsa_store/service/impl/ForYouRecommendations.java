package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * "Gợi ý cho bạn" ở trang chủ: lấy danh mục từ sản phẩm vừa xem (app gửi lên), đã thả tim và đã mua; chọn tối đa 3 danh
 * mục xuất hiện nhiều nhất rồi xen kẽ sản phẩm đang bán của các danh mục đó (mới nhất trước), bỏ hàng đã mua, đã thả tim
 * và vừa xem. Không có dữ liệu gì thì trả rỗng (app ẩn mục).
 */
@Service
@Transactional(readOnly = true)
public class ForYouRecommendations {

    static final int MAX_SEEDS = 30;
    static final int MAX_CATEGORIES = 3;

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final WishlistItemRepository wishlistRepository;
    private final AccessGuard accessGuard;
    private final CatalogPresenter catalog;

    public ForYouRecommendations(ProductRepository productRepository,
                                 OrderItemRepository orderItemRepository,
                                 WishlistItemRepository wishlistRepository,
                                 AccessGuard accessGuard,
                                 CatalogPresenter catalog) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.accessGuard = accessGuard;
        this.catalog = catalog;
    }

    public List<ProductResponse> forYou(List<Long> recentIds, int size) {
        int limit = Math.min(Math.max(size, 1), 20);
        List<Long> recent = recentIds == null ? List.of() : recentIds.stream().filter(Objects::nonNull).distinct()
                .limit(MAX_SEEDS).toList();
        User user = accessGuard.currentUserOrEmpty().filter(u -> u.getRole() == Role.USER).orElse(null);
        List<Long> purchased = user == null ? List.of() : orderItemRepository.purchasedProductIds(user.getId());
        List<Long> liked = user == null ? List.of() : wishlistRepository.productIds(user.getId());

        List<Long> seeds = new ArrayList<>(recent);
        seeds.addAll(liked);
        seeds.addAll(purchased);
        if (seeds.isEmpty()) {
            return List.of();
        }
        // Đếm danh mục theo số lần xuất hiện trong hạt giống; hoà thì danh mục xuất hiện trước (vừa xem) thắng.
        Map<Long, Long> categoryOf = new HashMap<>();
        productRepository.findAllById(new LinkedHashSet<>(seeds)).forEach(p -> {
            if (p.getCategory() != null) {
                categoryOf.put(p.getId(), p.getCategory().getId());
            }
        });
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long seed : seeds) {
            Long category = categoryOf.get(seed);
            if (category != null) {
                counts.merge(category, 1, Integer::sum);
            }
        }
        List<Long> categories = counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(MAX_CATEGORIES)
                .map(Map.Entry::getKey)
                .toList();
        if (categories.isEmpty()) {
            return List.of();
        }

        Set<Long> skip = new HashSet<>(seeds);
        List<Deque<Product>> queues = categories.stream()
                .map(c -> new ArrayDeque<>(productRepository.findPublic(c, null, PageRequest.of(0, limit * 2))))
                .collect(Collectors.toCollection(ArrayList::new));
        // Xen kẽ các danh mục để gợi ý không dồn hết một loại.
        LinkedHashMap<Long, Product> picked = new LinkedHashMap<>();
        boolean added = true;
        while (picked.size() < limit && added) {
            added = false;
            for (Deque<Product> queue : queues) {
                Product next = queue.pollFirst();
                while (next != null && (skip.contains(next.getId()) || picked.containsKey(next.getId()))) {
                    next = queue.pollFirst();
                }
                if (next != null && picked.size() < limit) {
                    picked.put(next.getId(), next);
                    added = true;
                }
            }
        }
        return catalog.products(List.copyOf(picked.values()));
    }
}
