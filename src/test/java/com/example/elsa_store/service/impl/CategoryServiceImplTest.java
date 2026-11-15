package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.CategoryRequest;
import com.example.elsa_store.dto.response.CategoryResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Danh mục 2 tầng: cha phải là tầng 1, không tầng 3, tên không trùng cùng cấp, chặn xoá không an toàn, cây sắp xếp. */
class CategoryServiceImplTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(categories, mock(AccessGuard.class), products);

    private Category shirts;
    private Category shortSleeve;
    private Category pants;

    private static Category category(long id, String name, Category parent) {
        Category c = Category.builder().name(name).parent(parent).children(new ArrayList<>()).build();
        c.setId(id);
        if (parent != null) {
            parent.getChildren().add(c);
        }
        return c;
    }

    private static CategoryRequest request(String name, Long parentId) {
        CategoryRequest r = new CategoryRequest();
        r.setName(name);
        r.setParentId(parentId);
        return r;
    }

    @BeforeEach
    void setUp() {
        shirts = category(1, "Áo sơ mi", null);
        shortSleeve = category(2, "Sơ mi ngắn tay", shirts);
        pants = category(3, "Quần", null);
        List<Category> all = List.of(pants, shortSleeve, shirts);
        when(categories.findAll()).thenReturn(all);
        all.forEach(c -> when(categories.findById(c.getId())).thenReturn(Optional.of(c)));
        when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(products.countByCategory()).thenReturn(List.<Object[]>of(new Object[] {2L, 5L}));
    }

    @Test
    void listIsATreeParentsThenTheirChildren() {
        List<CategoryResponse> list = service.getAll();
        assertEquals(List.of("Áo sơ mi", "Sơ mi ngắn tay", "Quần"), list.stream().map(CategoryResponse::getName).toList());
        assertEquals(1, list.get(0).getLevel());
        assertEquals(1, list.get(0).getChildCount());
        assertEquals(2, list.get(1).getLevel());
        assertEquals("Áo sơ mi", list.get(1).getParentName());
        assertEquals(5L, list.get(1).getProductCount());
        assertEquals(0L, list.get(2).getProductCount());
    }

    @Test
    void childOfATopLevelCategoryIsCreated() {
        CategoryResponse res = service.create(request("  Sơ mi   dài tay ", 1L));
        assertEquals("Sơ mi dài tay", res.getName());
        assertEquals(1L, res.getParentId());
        assertEquals(2, res.getLevel());
    }

    @Test
    void noThirdLevelNoSelfParentNoDuplicates() {
        assertThrows(AppException.class, () -> service.create(request("Tay lỡ", 2L)), "cha là tầng 2 → tầng 3 bị chặn");
        assertThrows(AppException.class, () -> service.update(1L, request("Áo sơ mi", 1L)), "làm cha của chính nó");
        assertThrows(AppException.class, () -> service.create(request("sơ MI ngắn tay", 1L)), "trùng tên cùng cấp");
        assertThrows(AppException.class, () -> service.create(request("   ", null)));
        // Trùng tên nhưng khác cấp thì được.
        assertEquals(1, service.create(request("Sơ mi ngắn tay", null)).getLevel());
    }

    @Test
    void parentWithChildrenStaysTopLevel() {
        AppException e = assertThrows(AppException.class, () -> service.update(1L, request("Áo sơ mi", 3L)));
        assertTrue(e.getMessage().contains("subcategories"), e.getMessage());
        // Danh mục con chuyển sang cha khác / lên tầng 1 thì được.
        assertEquals(3L, service.update(2L, request("Sơ mi ngắn tay", 3L)).getParentId());
        assertNull(service.update(2L, request("Sơ mi ngắn tay", null)).getParentId());
    }

    @Test
    void deleteIsBlockedWhileItHasChildrenOrProducts() {
        assertThrows(AppException.class, () -> service.delete(1L));
        when(products.countByCategory_Id(2L)).thenReturn(5L);
        AppException used = assertThrows(AppException.class, () -> service.delete(2L));
        assertTrue(used.getMessage().contains("5 products"), used.getMessage());
        verify(categories, never()).delete(any());

        service.delete(3L);
        verify(categories).delete(pants);
    }
}
