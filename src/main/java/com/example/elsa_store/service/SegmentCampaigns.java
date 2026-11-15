package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.SegmentOfferRequest;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import com.example.elsa_store.dto.response.SegmentCampaignResponse;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.SegmentCampaign;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.PromotionRepository;
import com.example.elsa_store.repository.SegmentCampaignRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Chủ / quản lý gửi ưu đãi (lời nhắn, có thể kèm một mã giảm giá đang chạy của cửa hàng) cho mọi khách có tài khoản trong
 * một nhóm (xem {@link CustomerSegments}): thông báo trong app + push loại {@code STORE_OFFER}, khách tắt được ở nhóm
 * "Ưu đãi từ cửa hàng". Mỗi nhóm của một cửa hàng gửi tối đa một lần mỗi {@value #COOLDOWN_HOURS} giờ, tối đa
 * {@value #MAX_RECIPIENTS} người một lần, để không thành spam.
 */
@Service
@RequiredArgsConstructor
public class SegmentCampaigns {

    static final int COOLDOWN_HOURS = 24;
    static final int MAX_RECIPIENTS = 500;

    private final AccessGuard accessGuard;
    private final CustomerSegments customerSegments;
    private final PromotionRepository promotionRepository;
    private final UserRepository userRepository;
    private final SegmentCampaignRepository campaignRepository;
    private final NotificationService notificationService;

    @Transactional
    public SegmentCampaignResponse send(CustomerSegment segment, SegmentOfferRequest request) {
        Store store = accessGuard.requireStoreManager();
        User sender = accessGuard.currentUser();
        if (campaignRepository.existsByStore_IdAndSegmentAndCreatedAtAfter(
                store.getId(), segment, LocalDateTime.now().minusHours(COOLDOWN_HOURS))) {
            throw new AppException(ErrorCode.CAMPAIGN_TOO_SOON);
        }
        String coupon = request.promotionId() == null ? null : activeCoupon(request.promotionId(), store);
        List<Long> userIds = customerSegments.list(segment).customers().stream()
                .map(CustomerSegmentsResponse.Item::userId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_RECIPIENTS)
                .toList();
        List<User> recipients = userRepository.findAllById(userIds).stream()
                .filter(u -> u.isEnabled() && u.getRole() == Role.USER)
                .toList();
        if (recipients.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "No customer with an account in this segment");
        }
        String message = request.message().strip();
        NotificationMessages.Message note = NotificationMessages.storeOffer(store.getName(), message, coupon);
        for (User user : recipients) {
            notificationService.notify(user, NotificationType.STORE_OFFER, note, AppLinks.store(store.getId()), store.getId());
        }
        SegmentCampaign saved = campaignRepository.save(SegmentCampaign.builder()
                .store(store)
                .segment(segment)
                .message(message)
                .couponCode(coupon)
                .recipients(recipients.size())
                .sentBy(sender)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SegmentCampaignResponse> history() {
        Store store = accessGuard.requireStoreManager();
        return campaignRepository.findTop20ByStore_IdOrderByIdDesc(store.getId()).stream().map(SegmentCampaigns::toResponse).toList();
    }

    /** Mã giảm giá đang chạy của chính cửa hàng; không thì báo lỗi rõ. */
    private String activeCoupon(Long promotionId, Store store) {
        Promotion p = promotionRepository.findById(promotionId).orElse(null);
        LocalDate today = LocalDate.now();
        boolean ok = p != null
                && p.getStore() != null
                && p.getStore().getId().equals(store.getId())
                && p.getCouponCode() != null
                && !p.getCouponCode().isBlank()
                && Integer.valueOf(1).equals(p.getStatus())
                && (p.getStartDate() == null || !p.getStartDate().isAfter(today))
                && (p.getEndDate() == null || !p.getEndDate().isBefore(today));
        if (!ok) {
            throw new AppException(ErrorCode.BAD_REQUEST, "promotionId: must be a running coupon of your store");
        }
        return p.getCouponCode();
    }

    private static SegmentCampaignResponse toResponse(SegmentCampaign c) {
        return new SegmentCampaignResponse(c.getId(), c.getSegment(), c.getMessage(), c.getCouponCode(),
                c.getRecipients() == null ? 0 : c.getRecipients(), c.getCreatedAt());
    }
}
