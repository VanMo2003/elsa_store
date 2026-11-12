package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ProductImportResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Nhập sản phẩm từ Excel: gộp dòng cùng tên, thêm vào sản phẩm có sẵn, kiểm tra hết rồi mới ghi. */
class ProductImporterTest {

    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final StockService stock = mock(StockService.class);
    private final NewProductAlerts alerts = mock(NewProductAlerts.class);
    private final ProductImporter importer = new ProductImporter(accessGuard, categories, products, variants, stock, alerts);

    private Store store;
    private User owner;
    private Product existing;

    private static List<String> row(Object... cells) {
        List<String> out = new ArrayList<>();
        for (Object c : cells) {
            out.add(c == null ? "" : c.toString());
        }
        while (out.size() < ProductImporter.HEADERS.length) {
            out.add("");
        }
        return out;
    }

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Elsa Q1").build();
        store.setId(1L);
        owner = User.builder().username("chu").role(Role.STORE_OWNER).build();
        Category shoes = Category.builder().name("Giày").build();
        shoes.setId(4L);
        Category bags = Category.builder().name("Túi Xách").build();
        bags.setId(6L);
        when(categories.findAll()).thenReturn(List.of(shoes, bags));
        existing = Product.builder().name("Túi tote Elsa").store(store).category(bags).status(1).basePrice(350_000.0).build();
        existing.setId(50L);
        when(products.findAllByStore_Id(1L)).thenReturn(List.of(existing));
        when(products.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(100L + p.getName().length());
            return p;
        });
        when(variants.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accessGuard.requireStoreManager()).thenReturn(store);
        when(accessGuard.currentUser()).thenReturn(owner);
    }

    @Test
    void rowsWithTheSameNameBecomeOneProductAndExistingNamesGetNewVariants() {
        ProductImportResponse res = importer.importRows(List.of(
                row("Giày Run", "giày", "890.000", "Êm", "Đen", "40", "RUN-40", null, 10, "500000", "Đang bán"),
                row("  giày   run ", "4", "890000", null, "Đen", "41", "RUN-41", "920,000đ", null, null, null),
                row(),
                row("Giày Walk", "Giày", 650000, null, null, null, null, null, 0, null, "Ẩn"),
                row("Túi tote Elsa", "Túi xách", null, null, "Kem", null, "TOTE-KEM", null, 5, 120000, null)),
                store, owner, false);

        assertTrue(res.applied(), res.errors().toString());
        assertEquals(5, res.rows());
        assertEquals(2, res.productsCreated());
        assertEquals(1, res.productsExtended());
        assertEquals(4, res.variantsCreated());
        assertEquals(3, res.stockTracked());

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products, times(2)).save(saved.capture());
        Product run = saved.getAllValues().get(0);
        assertEquals("Giày Run", run.getName(), "lấy tên ở dòng đầu");
        assertEquals(890_000.0, run.getBasePrice());
        assertEquals("Êm", run.getDescription());
        assertSame(store, run.getStore());
        assertEquals(0, saved.getAllValues().get(1).getStatus(), "Ẩn");

        ArgumentCaptor<ProductVariant> v = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variants, times(4)).save(v.capture());
        assertSame(run, v.getAllValues().get(1).getProduct(), "dòng 2 gộp vào sản phẩm của dòng 1 (khác hoa thường, khoảng trắng)");
        assertEquals(920_000.0, v.getAllValues().get(1).getPrice());
        assertEquals(890_000.0, v.getAllValues().get(0).getPrice(), "trống giá phân loại = giá gốc");
        assertEquals(500_000.0, v.getAllValues().get(0).getCostPrice());
        assertSame(existing, v.getAllValues().get(3).getProduct(), "trùng tên sản phẩm có sẵn thì thêm phân loại");
        assertEquals(350_000.0, v.getAllValues().get(3).getPrice(),
                "sản phẩm có sẵn: không cần giá gốc, giá phân loại trống lấy giá gốc của sản phẩm đó");

        verify(stock).adjust(any(), eq(10), isNull(), eq("Nhập từ Excel"), eq(owner));
        verify(stock).adjust(any(), eq(0), isNull(), anyString(), eq(owner));
        verify(stock).adjust(any(), eq(5), isNull(), anyString(), eq(owner));
        verify(stock, times(3)).adjust(any(), anyInt(), any(), any(), any());
        verify(alerts).afterImport(store, List.of(run, saved.getAllValues().get(1)));
    }

    @Test
    void anyBadRowStopsTheWholeImportAndEveryProblemIsListedByRow() {
        when(variants.existsBySku("OLD-1")).thenReturn(true);

        ProductImportResponse res = importer.importRows(List.of(
                row("", "Giày", 100),
                row("Giày A", "Dép", 100),
                row("Giày B", "Giày", "abc", null, null, null, null, null, "1,5"),
                row("Giày C", "Giày", 100, null, null, null, "DUP"),
                row("Giày D", "Giày", 100, null, null, null, "dup"),
                row("Giày E", "Giày", 100, null, null, null, "OLD-1", null, null, null, "Ngừng"),
                row("Giày F", "Giày", 100),
                row("giày f", "Túi Xách", 100)),
                store, owner, false);

        assertFalse(res.applied());
        assertEquals(List.of(
                new ProductImportResponse.Issue(2, "thiếu tên sản phẩm"),
                new ProductImportResponse.Issue(3, "không có danh mục \"Dép\""),
                new ProductImportResponse.Issue(4, "giá gốc \"abc\" không phải số; tồn kho \"1,5\" không phải số nguyên"),
                new ProductImportResponse.Issue(6, "SKU dup bị lặp trong tệp"),
                new ProductImportResponse.Issue(7, "trạng thái \"Ngừng\" phải là Đang bán hoặc Ẩn; SKU OLD-1 đã có"),
                new ProductImportResponse.Issue(9, "khác danh mục với dòng 8 cùng sản phẩm")),
                res.errors());
        verify(products, never()).save(any());
        verify(variants, never()).save(any());
        verifyNoInteractions(stock, alerts);
    }

    @Test
    void dryRunCountsWithoutWritingAndEmptyOrHugeFilesAreRefused() {
        ProductImportResponse preview = importer.importRows(List.of(
                row("Giày Run", "Giày", 100, null, null, null, null, null, 3),
                row("Túi tote Elsa", "Túi Xách", 100)), store, owner, true);
        assertFalse(preview.applied());
        assertTrue(preview.errors().isEmpty());
        assertEquals((1 + 1 + 2 + 1), preview.productsCreated() + preview.productsExtended() + preview.variantsCreated()
                + preview.stockTracked());
        verify(products, never()).save(any());

        ProductImportResponse empty = importer.importRows(List.of(row(), row()), store, owner, false);
        assertEquals(List.of(new ProductImportResponse.Issue(2, "Tệp chưa có dòng dữ liệu nào (bắt đầu từ dòng 2)")),
                empty.errors());

        List<List<String>> huge = new ArrayList<>();
        for (int i = 0; i < ProductImporter.MAX_ROWS + 1; i++) {
            huge.add(row("SP " + i, "Giày", 100));
        }
        assertEquals("Tối đa 500 dòng mỗi lần nhập", importer.importRows(huge, store, owner, false).errors().getFirst().message());
    }

    @Test
    void pricesAcceptCommonVietnameseWritings() {
        List<String> problems = new ArrayList<>();
        assertEquals(890_000.0, ProductImporter.money("890.000", "giá", true, problems));
        assertEquals(890_000.0, ProductImporter.money("890,000 đ", "giá", true, problems));
        assertEquals(1_250_000.0, ProductImporter.money("1.250.000₫", "giá", true, problems));
        assertEquals(13.0, ProductImporter.money("12.5", "giá", true, problems), "số lẻ làm tròn đồng");
        assertTrue(problems.isEmpty(), problems.toString());
        assertNull(ProductImporter.money("0", "giá gốc", true, problems));
        assertNull(ProductImporter.money("", "giá phân loại", false, problems));
        assertEquals(List.of("giá gốc phải lớn hơn 0"), problems);
    }

    @Test
    void theTemplateIsReadBackAsValidRows() throws Exception {
        byte[] file = importer.template();
        List<List<String>> rows = ProductImporter.read(new ByteArrayInputStream(file));
        assertEquals(3, rows.size());
        assertEquals(Arrays.asList("Giày chạy bộ Elsa Run", "Giày", "890000", "Đế êm, thoáng khí", "Đen", "40", "RUN-DEN-40",
                "", "10", "500000", "Đang bán"), rows.getFirst());

        ProductImportResponse res = importer.importRows(rows, store, owner, true);
        assertTrue(res.errors().isEmpty(), res.errors().toString());
        assertEquals(1, res.productsCreated());
        assertEquals(3, res.variantsCreated());
    }
}
