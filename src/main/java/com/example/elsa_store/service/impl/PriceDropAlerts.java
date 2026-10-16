package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.PromotionProduct;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Sản phẩm bạn thích đang giảm giá": khi chủ / quản lý tạo hoặc sửa một khuyến mãi tự động (không mã) đang chạy hôm
 * nay, khách đã thả tim các sản phẩm <b>vừa được</b> khuyến mãi đó giảm giá nhận thông báo. Sản phẩm đã được chính khuyến
 * mãi này giảm từ trước thì không báo lại. Khuyến mãi hẹn ngày bắt đầu sau không báo (chưa có lịch chạy hằng ngày).
 */
@Component
public class PriceDropAlerts {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    public PriceDropAlerts(WishlistItemRepository wishlistRepository,
                           ProductRepository productRepository,
                           NotificationService notificationService) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
    }

    /** Khuyến mãi tự động (không mã) đang bật và trong thời gian chạy. */
    static boolean running(Promotion p, LocalDate today) {
        return p != null
                && p.getStatus() != null && p.getStatus() == PricingService.STATUS_ACTIVE
                && (p.getCouponCode() == null || p.getCouponCode().isBlank())
                && (p.getStartDate() == null || !p.getStartDate().isAfter(today))
                && (p.getEndDate() == null || !p.getEndDate().isBefore(today));
    }

    /** Sản phẩm đang được khuyến mãi này giảm giá (rỗng nếu khuyến mãi không chạy) — gọi trước khi sửa. */
    static Set<Long> discountedBy(Promotion p, LocalDate today) {
        if (!running(p, today) || p.getPromotionProducts() == null) {
            return Set.of();
        }
        return p.getPromotionProducts().stream()
                .map(PromotionProduct::getProduct)
                .map(Product::getId)
                .collect(Collectors.toSet());
    }

    /** Sau khi lưu: báo khách thả tim các sản phẩm trong {@code after} mà chưa có trong {@code before}. */
    public void afterChange(Promotion p, Set<Long> before, Collection<Long> after, LocalDate today) {
        if (!running(p, today) || after == null || after.isEmpty()) {
            return;
        }
        Set<Long> fresh = new LinkedHashSet<>(after);
        fresh.removeAll(before);
        if (fresh.isEmpty()) {
            return;
        }
        List<Product> products = productRepository.findAllById(fresh);
        for (Product product : products) {
            if (!ProductServiceImpl.isPublic(product)) {
                continue;
            }
            NotificationMessages.Message message = NotificationMessages.priceDrop(product, p);
            for (WishlistItem item : wishlistRepository.findAllByProduct_Id(product.getId())) {
                notificationService.notify(item.getUser(), NotificationType.PRICE_DROP, message,
                        AppLinks.product(product.getId()), product.getId());
            }
        }
    }
}
