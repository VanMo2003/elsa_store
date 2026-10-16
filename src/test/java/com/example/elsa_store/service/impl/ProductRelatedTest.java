package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductImageRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Sản phẩm tương tự: cùng danh mục trước, thiếu thì bù hàng cùng cửa hàng, không lặp, không gồm chính nó. "Mua cùng": giữ
 * thứ tự số đơn chung, bỏ sản phẩm đã ẩn.
 */
class ProductRelatedTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CatalogPresenter catalog = mock(CatalogPresenter.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, mock(CategoryRepository.class),
            mock(FileStorageService.class), mock(ProductImageRepository.class), mock(ProductVariantRepository.class),
            mock(AccessGuard.class), catalog, mock(WishlistItemRepository.class));

    private Store store;
    private Category shoes;

    private Product product(long id, Category category) {
        Product p = Product.builder().name("P" + id).status(1).store(store).category(category).build();
        p.setId(id);
        return p;
    }

    @BeforeEach
    void setUp() {
        store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(3L);
        shoes = new Category();
        shoes.setId(5L);
        when(catalog.products(any())).thenReturn(List.of());
        service.setOrderItemRepository(orderItems);
    }

    @SuppressWarnings("unchecked")
    private List<Long> picked() {
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(catalog).products(captor.capture());
        return captor.getValue().stream().map(Product::getId).toList();
    }

    @Test
    void sameCategoryFirstThenSameStoreWithoutDuplicatesOrItself() {
        Product self = product(10L, shoes);
        when(products.findById(10L)).thenReturn(Optional.of(self));
        when(products.findPublic(eq(5L), isNull(), any())).thenReturn(List.of(product(12L, shoes), self, product(11L, shoes)));
        when(products.findPublic(isNull(), eq(3L), any()))
                .thenReturn(List.of(product(20L, null), product(12L, shoes), self, product(19L, null)));

        service.related(10L, 4);
        assertEquals(List.of(12L, 11L, 20L, 19L), picked());
    }

    @Test
    void enoughInTheCategoryMeansNoStoreQueryAndTheSizeIsCapped() {
        Product self = product(10L, shoes);
        when(products.findById(10L)).thenReturn(Optional.of(self));
        when(products.findPublic(eq(5L), isNull(), any())).thenReturn(List.of(product(13L, shoes), product(12L, shoes)));

        service.related(10L, 2);
        assertEquals(List.of(13L, 12L), picked());
        verify(products, never()).findPublic(isNull(), eq(3L), any());
    }

    @Test
    void boughtTogetherKeepsTheCoPurchaseOrderAndSkipsHiddenProducts() {
        Product self = product(10L, shoes);
        Product hidden = product(31L, null);
        hidden.setStatus(0);
        when(products.findById(10L)).thenReturn(Optional.of(self));
        when(orderItems.boughtTogether(eq(10L), any())).thenReturn(List.of(
                new Object[] {32L, 5L}, new Object[] {31L, 4L}, new Object[] {30L, 2L}, new Object[] {33L, 1L}));
        // Kho trả theo thứ tự bất kỳ; dịch vụ phải giữ thứ tự "nhiều đơn chung nhất trước".
        when(products.findAllById(List.of(32L, 31L, 30L, 33L)))
                .thenReturn(List.of(product(30L, null), hidden, product(33L, null), product(32L, null)));

        service.boughtTogether(10L, 2);
        assertEquals(List.of(32L, 30L), picked());
    }

    @Test
    void hiddenProductsHaveNoRelatedList() {
        Product hidden = product(10L, shoes);
        hidden.setStatus(0);
        when(products.findById(10L)).thenReturn(Optional.of(hidden));
        assertThrows(ResourceNotFoundException.class, () -> service.related(10L, 8));
    }
}
