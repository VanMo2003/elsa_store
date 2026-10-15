package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.ProductImageResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductImage;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductImageRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Bộ ảnh sản phẩm: ảnh đại diện tự chọn khi chưa có, đổi ảnh đại diện, gỡ ảnh đại diện thì ảnh khác thay vào. */
class ProductImagesTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, mock(CategoryRepository.class), storage,
            images, mock(ProductVariantRepository.class), accessGuard, mock(CatalogPresenter.class),
            mock(WishlistItemRepository.class));

    private final List<ProductImage> gallery = new ArrayList<>();
    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("A").build();
        store.setId(3L);
        product = Product.builder().name("Giay").store(store).build();
        product.setId(10L);
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(images.findAllByProduct_IdOrderByIdAsc(10L)).thenAnswer(inv -> List.copyOf(gallery));
        when(images.countByProduct_Id(10L)).thenAnswer(inv -> (long) gallery.size());
        when(images.findByIdAndProduct_Id(any(), eq(10L))).thenAnswer(inv ->
                gallery.stream().filter(i -> i.getId().equals(inv.getArgument(0))).findFirst());
        doAnswer(inv -> gallery.remove((ProductImage) inv.getArgument(0))).when(images).delete(any());
        when(images.saveAll(anyList())).thenAnswer(inv -> {
            List<ProductImage> saved = inv.getArgument(0);
            for (ProductImage image : saved) {
                image.setId(100L + gallery.size());
                gallery.add(image);
            }
            return saved;
        });
        when(storage.storeProductImage(any())).thenAnswer(inv ->
                "/uploads/products/" + ((MultipartFile) inv.getArgument(0)).getOriginalFilename());
    }

    private static MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "image/png", new byte[]{1});
    }

    @Test
    void firstUploadBecomesTheCoverAndLaterUploadsDoNotReplaceIt() {
        service.uploadImages(10L, List.of(file("a.png"), file("b.png")));
        assertEquals("/uploads/products/a.png", product.getImageUrl());
        service.uploadImages(10L, List.of(file("c.png")));
        assertEquals("/uploads/products/a.png", product.getImageUrl());

        List<ProductImageResponse> list = service.images(10L);
        assertEquals(3, list.size());
        assertEquals(List.of(true, false, false), list.stream().map(ProductImageResponse::isCover).toList());
        verify(accessGuard, atLeastOnce()).authorize(store, Access.STAFF_WRITE);
        verify(accessGuard).authorize(store, Access.READ);
    }

    @Test
    void coverCanBeChangedAndRemovingItPromotesTheNextImage() {
        service.uploadImages(10L, List.of(file("a.png"), file("b.png"), file("c.png")));
        List<ProductImageResponse> list = service.setCover(10L, 101L);
        assertEquals("/uploads/products/b.png", product.getImageUrl());
        assertTrue(list.get(1).isCover());

        list = service.deleteImage(10L, 101L);
        assertEquals("/uploads/products/a.png", product.getImageUrl(), "ảnh còn lại đầu tiên thay làm đại diện");
        assertEquals(2, list.size());
        assertTrue(list.getFirst().isCover());

        service.deleteImage(10L, 102L);
        assertEquals("/uploads/products/a.png", product.getImageUrl(), "gỡ ảnh thường không đổi ảnh đại diện");
        service.deleteImage(10L, 100L);
        assertNull(product.getImageUrl(), "hết ảnh thì không còn ảnh đại diện");
    }

    @Test
    void imagesOfAnotherProductOrOverTheLimitAreRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.setCover(10L, 999L));
        assertThrows(AppException.class, () -> service.uploadImages(10L, List.of()));

        List<MultipartFile> eleven = new ArrayList<>();
        for (int i = 0; i < ProductServiceImpl.MAX_IMAGES + 1; i++) {
            eleven.add(file(i + ".png"));
        }
        assertThrows(AppException.class, () -> service.uploadImages(10L, eleven));
        verify(storage, never()).storeProductImage(any());
    }
}
