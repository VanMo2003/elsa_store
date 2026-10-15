package com.example.elsa_store.service.impl;

import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.PromotionProduct;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.PromotionProductRepository;
import com.example.elsa_store.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Tính giá bán từ khuyến mãi — nơi duy nhất: danh sách / chi tiết sản phẩm, giỏ hàng, báo giá lúc thanh toán và lúc
 * tạo đơn đều đi qua đây, nên khách thấy giá nào thì trả đúng giá đó.
 *
 * <p>Quy tắc:
 *
 * <ul>
 *   <li>Khuyến mãi <b>không mã</b>, <b>gắn sản phẩm</b>: giảm thẳng vào đơn giá của sản phẩm đó (mỗi sản phẩm lấy mức
 *       giảm có lợi nhất cho khách).
 *   <li>Khuyến mãi <b>không mã</b>, <b>không gắn sản phẩm</b>: giảm cho cả đơn khi đơn đạt giá trị tối thiểu (lấy mức
 *       có lợi nhất).
 *   <li><b>Mã giảm giá</b>: khách nhập ở bước thanh toán; thay cho khuyến mãi cả đơn tự động (không cộng dồn). Mã gắn
 *       sản phẩm chỉ giảm trên các sản phẩm đó.
 * </ul>
 *
 * Loại khuyến mãi theo quy ước của app quản trị: 1 = giảm số tiền, 2 = giảm phần trăm; trạng thái 1 = đang chạy.
 * Tiền làm tròn tới đồng.
 */
@Service
public class PricingService {

    public static final int TYPE_FIXED_AMOUNT = 1;
    public static final int TYPE_PERCENTAGE = 2;
    public static final int TYPE_FREE_SHIPPING = 3;
    public static final int STATUS_ACTIVE = 1;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PromotionRepository promotionRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final Clock clock;

    @Autowired
    public PricingService(
            PromotionRepository promotionRepository, PromotionProductRepository promotionProductRepository) {
        this(promotionRepository, promotionProductRepository, Clock.system(VN));
    }

    PricingService(
            PromotionRepository promotionRepository,
            PromotionProductRepository promotionProductRepository,
            Clock clock) {
        this.promotionRepository = promotionRepository;
        this.promotionProductRepository = promotionProductRepository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(VN));
    }

    /** Giá sau khuyến mãi tự động của một đơn giá; {@code promotion} null nếu không được giảm. */
    public record Discounted(double originalPrice, double salePrice, Promotion promotion) {
        public boolean discounted() {
            return promotion != null && salePrice < originalPrice;
        }

        public Integer percentOff() {
            if (!discounted() || originalPrice <= 0) {
                return null;
            }
            return (int) Math.round((originalPrice - salePrice) * 100 / originalPrice);
        }

        public String promotionName() {
            return promotion == null ? null : promotion.getName();
        }

        /** Ngày kết thúc tính trọn ngày: khuyến mãi hết hiệu lực lúc 00:00 ngày hôm sau. */
        public LocalDateTime promotionEndsAt() {
            return !discounted() || promotion.getEndDate() == null
                    ? null
                    : promotion.getEndDate().plusDays(1).atStartOfDay();
        }
    }

    /** Khuyến mãi tự động đang chạy, gom theo id sản phẩm — một truy vấn cho cả trang sản phẩm. */
    public Map<Long, List<Promotion>> productPromotions(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Promotion>> result = new HashMap<>();
        for (PromotionProduct pp : promotionProductRepository.findActiveAutomatic(new HashSet<>(productIds), today())) {
            result.computeIfAbsent(pp.getProduct().getId(), id -> new ArrayList<>()).add(pp.getPromotion());
        }
        return result;
    }

    /** Mức giảm có lợi nhất cho khách trong các khuyến mãi của sản phẩm. */
    public static Discounted best(Double price, List<Promotion> promotions) {
        double original = money(price == null ? 0 : price);
        Promotion chosen = null;
        double bestDiscount = 0;
        for (Promotion promotion : promotions == null ? List.<Promotion>of() : promotions) {
            double discount = discountOf(promotion, original);
            if (discount > bestDiscount) {
                bestDiscount = discount;
                chosen = promotion;
            }
        }
        return new Discounted(original, money(original - bestDiscount), chosen);
    }

    /** Số tiền giảm của một khuyến mãi trên số tiền {@code amount} (không bao giờ vượt quá {@code amount}). */
    static double discountOf(Promotion promotion, double amount) {
        if (promotion == null || promotion.getValue() == null || promotion.getValue() <= 0 || amount <= 0) {
            return 0;
        }
        int type = promotion.getType() == null ? 0 : promotion.getType();
        double discount =
                switch (type) {
                    case TYPE_PERCENTAGE -> amount * Math.min(promotion.getValue(), 100) / 100;
                    case TYPE_FIXED_AMOUNT -> promotion.getValue();
                    default -> 0; // miễn phí vận chuyển: app chưa tính phí giao hàng
                };
        return money(Math.min(discount, amount));
    }

    // ---------------------------------------------------------------- phí giao hàng

    /**
     * Phí giao của cửa hàng cho đơn có tiền hàng (sau mọi khoản giảm) là {@code goodsTotal}: 0 khi cửa hàng giao miễn
     * phí hoặc đơn đạt mức miễn phí giao.
     */
    public static double shippingFee(Store store, double goodsTotal) {
        double fee = store == null || store.getShippingFee() == null ? 0 : store.getShippingFee();
        if (fee <= 0) {
            return 0;
        }
        Double threshold = store.getFreeShippingThreshold();
        if (threshold != null && goodsTotal >= threshold) {
            return 0;
        }
        return money(fee);
    }

    // ---------------------------------------------------------------- báo giá một đơn

    public record LineRequest(ProductVariant variant, int quantity) {}

    public record QuoteLine(
            ProductVariant variant,
            int quantity,
            double originalUnitPrice,
            double unitPrice,
            double discount,
            double lineTotal,
            String promotionName) {}

    public record Quote(
            List<QuoteLine> lines,
            double originalSubtotal,
            double subtotal,
            double orderDiscount,
            String couponCode,
            String orderPromotionName,
            double total) {

        /** Tổng tiền được giảm (theo sản phẩm + cả đơn). */
        public double totalDiscount() {
            return money(originalSubtotal - total);
        }

        /** Mô tả ngắn các khoản giảm để hiện trong đơn. */
        public String discountNote() {
            List<String> parts = new ArrayList<>();
            double productDiscount = money(originalSubtotal - subtotal);
            if (productDiscount > 0) {
                parts.add("Giảm giá sản phẩm " + vnd(productDiscount));
            }
            if (orderDiscount > 0) {
                parts.add((couponCode != null ? "Mã " + couponCode : orderPromotionName) + " -" + vnd(orderDiscount));
            }
            return parts.isEmpty() ? null : String.join("; ", parts);
        }
    }

    /**
     * Báo giá cho các dòng hàng của <b>một</b> cửa hàng. {@code couponCode} rỗng = không dùng mã. Mã sai / hết hạn / chưa
     * đủ điều kiện thì ném lỗi kèm lý do để khách sửa.
     */
    public Quote quote(Store store, List<LineRequest> lines, String couponCode) {
        Map<Long, List<Promotion>> promos = productPromotions(lines.stream()
                .map(l -> l.variant().getProduct().getId())
                .toList());
        List<QuoteLine> quoted = new ArrayList<>();
        double originalSubtotal = 0;
        double subtotal = 0;
        for (LineRequest line : lines) {
            Product product = line.variant().getProduct();
            Double price = line.variant().getPrice() != null ? line.variant().getPrice() : product.getBasePrice();
            Discounted unit = best(price, promos.get(product.getId()));
            double lineOriginal = money(unit.originalPrice() * line.quantity());
            double lineTotal = money(unit.salePrice() * line.quantity());
            quoted.add(new QuoteLine(
                    line.variant(),
                    line.quantity(),
                    unit.originalPrice(),
                    unit.salePrice(),
                    money(lineOriginal - lineTotal),
                    lineTotal,
                    unit.discounted() ? unit.promotionName() : null));
            originalSubtotal += lineOriginal;
            subtotal += lineTotal;
        }
        originalSubtotal = money(originalSubtotal);
        subtotal = money(subtotal);

        String code = couponCode == null || couponCode.isBlank() ? null : couponCode.trim();
        double orderDiscount = 0;
        String orderPromotionName = null;
        String appliedCode = null;
        if (code != null) {
            Promotion coupon = findUsableCoupon(store, code, subtotal);
            double eligible = eligibleAmount(coupon, quoted, subtotal);
            if (eligible <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Coupon does not apply to these products");
            }
            orderDiscount = discountOf(coupon, eligible);
            orderPromotionName = coupon.getName();
            appliedCode = coupon.getCouponCode();
        } else if (store != null) {
            for (Promotion promotion : promotionRepository.findActiveStoreWide(store.getId(), today())) {
                double min = promotion.getMinOrderValue() == null ? 0 : promotion.getMinOrderValue();
                double discount = subtotal >= min ? discountOf(promotion, subtotal) : 0;
                if (discount > orderDiscount) {
                    orderDiscount = discount;
                    orderPromotionName = promotion.getName();
                }
            }
        }
        double total = money(Math.max(0, subtotal - orderDiscount));
        return new Quote(quoted, originalSubtotal, subtotal, money(orderDiscount), appliedCode, orderPromotionName, total);
    }

    private Promotion findUsableCoupon(Store store, String code, double subtotal) {
        if (store == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid coupon code");
        }
        Promotion coupon = promotionRepository.findByStoreAndCoupon(store.getId(), code).stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Invalid coupon code"));
        LocalDate today = today();
        if (coupon.getStatus() == null || coupon.getStatus() != STATUS_ACTIVE
                || (coupon.getEndDate() != null && coupon.getEndDate().isBefore(today))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Coupon code has expired");
        }
        if (coupon.getStartDate() != null && coupon.getStartDate().isAfter(today)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Coupon code is not active yet");
        }
        if (coupon.getType() != null && coupon.getType() == TYPE_FREE_SHIPPING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Free-shipping coupons are not supported yet");
        }
        double min = coupon.getMinOrderValue() == null ? 0 : coupon.getMinOrderValue();
        if (subtotal < min) {
            // Có số tiền cụ thể nên viết sẵn tiếng Việt (app hiển thị nguyên văn).
            throw new AppException(
                    ErrorCode.BAD_REQUEST, "Đơn hàng cần tối thiểu " + vnd(min) + " để dùng mã " + coupon.getCouponCode());
        }
        return coupon;
    }

    /** Số tiền được tính giảm của mã: cả đơn, hoặc chỉ các sản phẩm mà mã gắn với. */
    private double eligibleAmount(Promotion coupon, List<QuoteLine> lines, double subtotal) {
        Set<Long> productIds = new HashSet<>();
        for (PromotionProduct pp : promotionProductRepository.findAllByPromotion_Id(coupon.getId())) {
            productIds.add(pp.getProduct().getId());
        }
        if (productIds.isEmpty()) {
            return subtotal;
        }
        return money(lines.stream()
                .filter(l -> productIds.contains(l.variant().getProduct().getId()))
                .mapToDouble(QuoteLine::lineTotal)
                .sum());
    }

    /** Làm tròn tới đồng. */
    static double money(double value) {
        return Math.round(value);
    }

    static String vnd(double amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(Math.round(amount)) + "đ";
    }
}
