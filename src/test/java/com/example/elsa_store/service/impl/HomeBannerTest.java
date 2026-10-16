package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.BannerLinkType;
import com.example.elsa_store.dto.request.BannerRequest;
import com.example.elsa_store.dto.response.BannerResponse;
import com.example.elsa_store.entity.HomeBanner;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.HomeBannerRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Banner trang chủ: liên kết phải trỏ tới thứ có thật, thời gian hợp lệ; chỉ ADMIN sửa; khách thấy banner đang hiện. */
class HomeBannerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final HomeBannerRepository banners = mock(HomeBannerRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final HomeBannerServiceImpl service =
            new HomeBannerServiceImpl(banners, products, stores, categories, storage, accessGuard, CLOCK);

    @BeforeEach
    void setUp() {
        when(banners.save(any())).thenAnswer(inv -> {
            HomeBanner b = inv.getArgument(0);
            if (b.getId() == null) b.setId(1L);
            return b;
        });
    }

    private static BannerRequest request(BannerLinkType type, String value) {
        return BannerRequest.builder().title(" Sale 10.10 ").subtitle("  ").linkType(type).linkValue(value).build();
    }

    @Test
    void linksMustPointToSomethingReal() {
        when(products.existsById(10L)).thenReturn(true);
        BannerResponse res = service.create(request(BannerLinkType.PRODUCT, " 10 "));
        assertEquals("Sale 10.10", res.title());
        assertNull(res.subtitle());
        assertEquals("10", res.linkValue());
        assertTrue(res.active(), "mặc định bật");

        assertThrows(AppException.class, () -> service.create(request(BannerLinkType.STORE, "99")));
        assertThrows(AppException.class, () -> service.create(request(BannerLinkType.CATEGORY, "abc")));
        assertThrows(AppException.class, () -> service.create(request(BannerLinkType.SEARCH, "  ")));
        assertNull(service.create(request(BannerLinkType.NONE, "bỏ qua")).linkValue());
    }

    @Test
    void endMustBeAfterStartAndListIsCapped() {
        BannerRequest bad = request(BannerLinkType.NONE, null);
        bad.setStartsAt(LocalDateTime.of(2026, 10, 20, 0, 0));
        bad.setEndsAt(LocalDateTime.of(2026, 10, 19, 0, 0));
        assertThrows(AppException.class, () -> service.create(bad));

        when(banners.count()).thenReturn(20L);
        assertThrows(AppException.class, () -> service.create(request(BannerLinkType.NONE, null)));
    }

    @Test
    void customersSeeShowingBannersAndOnlyAdminsManage() {
        when(banners.findShowing(LocalDateTime.of(2026, 10, 16, 12, 0)))
                .thenReturn(List.of(HomeBanner.builder().title("A").active(true).build()));
        assertEquals(List.of("A"), service.showing().stream().map(BannerResponse::title).toList());

        HomeBanner existing = HomeBanner.builder().title("A").build();
        existing.setId(3L);
        when(banners.findById(3L)).thenReturn(Optional.of(existing));
        when(storage.storeBannerImage(any())).thenReturn("/uploads/banners/a.png");
        assertEquals("/uploads/banners/a.png",
                service.updateImage(3L, new MockMultipartFile("files", "a.png", "image/png", new byte[] {1})).imageUrl());

        when(accessGuard.requireAdmin()).thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.delete(3L));
        assertThrows(AppException.class, service::all);
        verify(banners, never()).delete(any());
    }
}
