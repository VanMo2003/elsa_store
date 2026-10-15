package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.request.PromotionRequest;
import com.example.elsa_store.dto.response.PromotionResponse;
import com.example.elsa_store.entity.Promotion;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class PromotionMapper {

    public static Promotion toEntity(PromotionRequest req) {
        Promotion p = new Promotion();
        update(p, req);
        return p;
    }

    public static void update(Promotion p, PromotionRequest req) {
        p.setName(req.getName() == null ? null : req.getName().trim());
        p.setDescription(req.getDescription());
        p.setType(req.getType());
        p.setValue(req.getValue());
        p.setStartDate(req.getStartDate());
        p.setEndDate(req.getEndDate());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setCouponCode(normalizeCoupon(req.getCouponCode()));
        p.setMinOrderValue(req.getMinOrderValue());
    }

    /** Mã giảm giá lưu viết hoa, bỏ khoảng trắng; chuỗi rỗng = không có mã (khuyến mãi tự động). */
    public static String normalizeCoupon(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static PromotionResponse toResponse(Promotion p) {
        return toResponse(p, null);
    }

    public static PromotionResponse toResponse(Promotion p, LocalDate today) {
        PromotionResponse res = new PromotionResponse();
        res.setId(p.getId());
        res.setName(p.getName());
        res.setDescription(p.getDescription());
        res.setType(p.getType());
        res.setValue(p.getValue());
        res.setStartDate(p.getStartDate());
        res.setEndDate(p.getEndDate());
        res.setStatus(p.getStatus());
        res.setCouponCode(p.getCouponCode());
        res.setMinOrderValue(p.getMinOrderValue());
        res.setProductIds(p.getPromotionProducts() == null
                ? List.of()
                : p.getPromotionProducts().stream()
                        .map(pp -> pp.getProduct().getId())
                        .toList());
        if (today != null) {
            res.setActiveNow(p.getStatus() != null
                    && p.getStatus() == 1
                    && (p.getStartDate() == null || !p.getStartDate().isAfter(today))
                    && (p.getEndDate() == null || !p.getEndDate().isBefore(today)));
        }
        return res;
    }
}
