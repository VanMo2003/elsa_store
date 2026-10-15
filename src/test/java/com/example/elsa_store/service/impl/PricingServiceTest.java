package com.example.elsa_store.service.impl;

import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.PromotionProduct;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.PromotionProductRepository;
import com.example.elsa_store.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PromotionProductRepository links = mock(PromotionProductRepository.class);
    private final PricingService pricing = new PricingService(
            promotions, links, Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("UTC")));

    private Store store;
    private Product shoes;
    private Product shirt;
    private ProductVariant shoes42;
    private ProductVariant shirtM;
    private final List<PromotionProduct> automaticLinks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Store A").build();
        store.setId(1L);
        shoes = Product.builder().name("Giày chạy bộ").basePrice(1_800_000.0).store(store).build();
        shoes.setId(10L);
        shirt = Product.builder().name("Áo thun").basePrice(150_000.0).store(store).build();
        shirt.setId(11L);
        shoes42 = ProductVariant.builder().product(shoes).price(1_800_000.0).size("42").build();
        shoes42.setId(100L);
        shirtM = ProductVariant.builder().product(shirt).price(null).size("M").build();
        shirtM.setId(101L);
        when(links.findActiveAutomatic(any(), eq(TODAY))).thenAnswer(inv -> automaticLinks);
        when(promotions.findActiveStoreWide(anyLong(), eq(TODAY))).thenReturn(List.of());
        when(promotions.findByStoreAndCoupon(anyLong(), anyString())).thenReturn(List.of());
        when(links.findAllByPromotion_Id(anyLong())).thenReturn(List.of());
    }

    private static Promotion promo(long id, int type, double value) {
        Promotion p = Promotion.builder().name("KM " + id).type(type).value(value).status(1).build();
        p.setId(id);
        return p;
    }

    private void attach(Promotion promotion, Product product) {
        PromotionProduct link = PromotionProduct.builder().promotion(promotion).product(product).build();
        automaticLinks.add(link);
    }

    @Test
    void shippingFeeIsWaivedFromTheThresholdAndForFreeShippingStores() {
        assertEquals(0, PricingService.shippingFee(store, 100_000));
        store.setShippingFee(25_000.4);
        assertEquals(25_000, PricingService.shippingFee(store, 100_000));
        store.setFreeShippingThreshold(500_000.0);
        assertEquals(25_000, PricingService.shippingFee(store, 499_999));
        assertEquals(0, PricingService.shippingFee(store, 500_000));
        assertEquals(0, PricingService.shippingFee(null, 100_000));
    }

    @Test
    void bestPicksTheLargestDiscountAndNeverGoesBelowZero() {
        PricingService.Discounted price = PricingService.best(
                1_800_000.0,
                List.of(promo(1, PricingService.TYPE_PERCENTAGE, 10), promo(2, PricingService.TYPE_FIXED_AMOUNT, 300_000)));
        assertEquals(1_500_000, price.salePrice());
        assertEquals(17, price.percentOff());
        assertEquals("KM 2", price.promotionName());

        PricingService.Discounted free = PricingService.best(100_000.0, List.of(promo(3, PricingService.TYPE_FIXED_AMOUNT, 999_999)));
        assertEquals(0, free.salePrice());

        PricingService.Discounted capped = PricingService.best(100_000.0, List.of(promo(4, PricingService.TYPE_PERCENTAGE, 250)));
        assertEquals(0, capped.salePrice());

        PricingService.Discounted none = PricingService.best(100_000.0, List.of());
        assertFalse(none.discounted());
        assertNull(none.percentOff());
        assertNull(none.promotionEndsAt());
    }

    @Test
    void promotionEndsAtMidnightAfterItsLastDay() {
        Promotion limited = promo(5, PricingService.TYPE_PERCENTAGE, 10);
        limited.setEndDate(java.time.LocalDate.of(2026, 10, 20));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 21, 0, 0),
                PricingService.best(100_000.0, List.of(limited)).promotionEndsAt());
        assertNull(PricingService.best(100_000.0, List.of(promo(6, PricingService.TYPE_PERCENTAGE, 10))).promotionEndsAt(),
                "không có ngày kết thúc thì không đếm ngược");
    }

    @Test
    void productPromotionLowersTheUnitPriceOfThatProductOnly() {
        attach(promo(1, PricingService.TYPE_PERCENTAGE, 20), shoes);

        PricingService.Quote quote = pricing.quote(store, List.of(
                new PricingService.LineRequest(shoes42, 2), new PricingService.LineRequest(shirtM, 3)), null);

        PricingService.QuoteLine shoesLine = quote.lines().get(0);
        assertEquals(1_800_000, shoesLine.originalUnitPrice());
        assertEquals(1_440_000, shoesLine.unitPrice());
        assertEquals(720_000, shoesLine.discount());
        assertEquals(2_880_000, shoesLine.lineTotal());
        // Biến thể chưa đặt giá dùng giá cơ bản của sản phẩm.
        assertEquals(150_000, quote.lines().get(1).unitPrice());
        assertEquals(4_050_000, quote.originalSubtotal());
        assertEquals(3_330_000, quote.subtotal());
        assertEquals(3_330_000, quote.total());
        assertEquals(720_000, quote.totalDiscount());
        assertTrue(quote.discountNote().startsWith("Giảm giá sản phẩm"));
    }

    @Test
    void storeWidePromotionAppliesWhenTheOrderIsBigEnough() {
        Promotion tenPercent = promo(5, PricingService.TYPE_PERCENTAGE, 10);
        tenPercent.setMinOrderValue(1_000_000.0);
        when(promotions.findActiveStoreWide(1L, TODAY)).thenReturn(List.of(tenPercent));

        PricingService.Quote small = pricing.quote(store, List.of(new PricingService.LineRequest(shirtM, 2)), null);
        assertEquals(0, small.orderDiscount());
        assertEquals(300_000, small.total());

        PricingService.Quote big = pricing.quote(store, List.of(new PricingService.LineRequest(shoes42, 1)), null);
        assertEquals(180_000, big.orderDiscount());
        assertEquals(1_620_000, big.total());
        assertEquals("KM 5", big.orderPromotionName());
    }

    @Test
    void couponReplacesTheAutomaticOrderDiscount() {
        Promotion auto = promo(5, PricingService.TYPE_PERCENTAGE, 10);
        when(promotions.findActiveStoreWide(1L, TODAY)).thenReturn(List.of(auto));
        Promotion coupon = promo(6, PricingService.TYPE_FIXED_AMOUNT, 50_000);
        coupon.setCouponCode("GIAM50K");
        when(promotions.findByStoreAndCoupon(1L, "giam50k")).thenReturn(List.of(coupon));

        PricingService.Quote quote = pricing.quote(store, List.of(new PricingService.LineRequest(shoes42, 1)), " giam50k ");
        assertEquals(50_000, quote.orderDiscount());
        assertEquals("GIAM50K", quote.couponCode());
        assertEquals(1_750_000, quote.total());
        assertTrue(quote.discountNote().contains("Mã GIAM50K"));
    }

    @Test
    void couponRulesAreEnforcedWithClearReasons() {
        Promotion expired = promo(7, PricingService.TYPE_PERCENTAGE, 10);
        expired.setCouponCode("OLD");
        expired.setEndDate(TODAY.minusDays(1));
        when(promotions.findByStoreAndCoupon(1L, "OLD")).thenReturn(List.of(expired));
        Promotion future = promo(8, PricingService.TYPE_PERCENTAGE, 10);
        future.setCouponCode("SOON");
        future.setStartDate(TODAY.plusDays(2));
        when(promotions.findByStoreAndCoupon(1L, "SOON")).thenReturn(List.of(future));
        Promotion big = promo(9, PricingService.TYPE_FIXED_AMOUNT, 100_000);
        big.setCouponCode("BIG");
        big.setMinOrderValue(5_000_000.0);
        when(promotions.findByStoreAndCoupon(1L, "BIG")).thenReturn(List.of(big));

        List<PricingService.LineRequest> lines = List.of(new PricingService.LineRequest(shoes42, 1));
        assertEquals("Invalid coupon code", assertThrows(AppException.class, () -> pricing.quote(store, lines, "NOPE")).getMessage());
        assertEquals("Coupon code has expired", assertThrows(AppException.class, () -> pricing.quote(store, lines, "OLD")).getMessage());
        assertEquals("Coupon code is not active yet", assertThrows(AppException.class, () -> pricing.quote(store, lines, "SOON")).getMessage());
        AppException min = assertThrows(AppException.class, () -> pricing.quote(store, lines, "BIG"));
        assertTrue(min.getMessage().contains("tối thiểu 5.000.000đ"), min.getMessage());
    }

    @Test
    void productScopedCouponOnlyDiscountsThoseProducts() {
        Promotion shirtsOnly = promo(10, PricingService.TYPE_PERCENTAGE, 50);
        shirtsOnly.setCouponCode("AO50");
        when(promotions.findByStoreAndCoupon(1L, "AO50")).thenReturn(List.of(shirtsOnly));
        when(links.findAllByPromotion_Id(10L)).thenReturn(List.of(
                PromotionProduct.builder().promotion(shirtsOnly).product(shirt).build()));

        PricingService.Quote quote = pricing.quote(store, List.of(
                new PricingService.LineRequest(shoes42, 1), new PricingService.LineRequest(shirtM, 2)), "AO50");
        assertEquals(150_000, quote.orderDiscount());
        assertEquals(1_950_000, quote.total());

        AppException ex = assertThrows(AppException.class,
                () -> pricing.quote(store, List.of(new PricingService.LineRequest(shoes42, 1)), "AO50"));
        assertEquals("Coupon does not apply to these products", ex.getMessage());
    }

    @Test
    void moneyIsRoundedToWholeDong() {
        attach(promo(11, PricingService.TYPE_PERCENTAGE, 33), shirt);
        PricingService.Quote quote = pricing.quote(store, List.of(new PricingService.LineRequest(shirtM, 1)), null);
        assertEquals(100_500, quote.lines().getFirst().unitPrice());
        assertEquals(quote.total(), Math.rint(quote.total()));
    }
}
