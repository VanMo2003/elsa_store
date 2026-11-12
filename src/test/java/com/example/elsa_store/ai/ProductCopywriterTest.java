package com.example.elsa_store.ai;

import com.example.elsa_store.dto.request.ProductDescriptionRequest;
import com.example.elsa_store.dto.response.ProductDescriptionResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.security.AccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AI viết mô tả sản phẩm: thông tin đưa cho mô hình, mẫu soạn sẵn, dọn markdown và quyền với sản phẩm đang sửa. */
class ProductCopywriterTest {

    private static Store store(long id) {
        Store store = new Store();
        store.setId(id);
        return store;
    }

    private static ProductVariant variant(String color, String size) {
        ProductVariant v = new ProductVariant();
        v.setColor(color);
        v.setSize(size);
        return v;
    }

    private static ProductCopywriter writer(AiClient ai, Store mine, Product existing) {
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireStoreMember()).thenReturn(mine);
        User user = new User();
        user.setId(5L);
        when(guard.currentUser()).thenReturn(user);
        ProductRepository products = mock(ProductRepository.class);
        if (existing != null) {
            when(products.findById(existing.getId())).thenReturn(Optional.of(existing));
        }
        CategoryRepository categories = mock(CategoryRepository.class);
        Category shoes = new Category();
        shoes.setName("Giày thể thao");
        when(categories.findById(3L)).thenReturn(Optional.of(shoes));
        return new ProductCopywriter(ai, guard, products, categories, new AiRateLimiter());
    }

    private static Product existing(Store owner) {
        Product p = new Product();
        p.setId(9L);
        p.setStore(owner);
        p.setVariants(List.of(variant("Đen", "40"), variant("Đen", "41"), variant("Trắng", " 41 "), variant(null, "")));
        return p;
    }

    private static AiClient off() {
        return new AiClient(new ObjectMapper(), "", "", "", "", "", "", "", "", "", 5);
    }

    @Test
    void briefCollectsCategoryAndDistinctColoursAndSizes() {
        Store mine = store(1);
        var brief = writer(off(), mine, existing(mine))
                .brief(new ProductDescriptionRequest(" Giày chạy bộ Elsa ", 3L, 9L, 890000.0, " êm, nhẹ "), mine);
        assertEquals("Giày chạy bộ Elsa", brief.name());
        assertEquals("Giày thể thao", brief.category());
        assertEquals(List.of("Đen", "Trắng"), brief.colors());
        assertEquals(List.of("40", "41"), brief.sizes());
        assertEquals("êm, nhẹ", brief.notes());
        String prompt = ProductCopywriter.prompt(brief);
        assertTrue(prompt.contains("Danh mục: Giày thể thao\nTầm giá: 890.000đ (không nhắc trong mô tả)\nMàu có sẵn: Đen, Trắng\n"
                + "Cỡ có sẵn: 40, 41\nGợi ý của người bán: êm, nhẹ\n</product>"), prompt);
    }

    @Test
    void anotherStoresProductIsRejected() {
        Product theirs = existing(store(2));
        var writer = writer(off(), store(1), theirs);
        var e = assertThrows(AppException.class,
                () -> writer.write(new ProductDescriptionRequest("Giày", null, 9L, null, null)));
        assertEquals(ErrorCode.UN_AUTHORIZED, e.getErrorCode());
    }

    @Test
    void withoutAnApiKeyTheTemplateUsesOnlyWhatWasEntered() {
        Store mine = store(1);
        ProductDescriptionResponse out = writer(off(), mine, existing(mine))
                .write(new ProductDescriptionRequest("Giày chạy bộ Elsa", 3L, 9L, null, "đế cao su\n chống trượt"));
        assertFalse(out.ai());
        assertTrue(out.notice().contains("chưa bật trợ lý AI"));
        assertEquals("Giày chạy bộ Elsa thuộc dòng giày thể thao, dễ phối cho nhiều dịp.\n• đế cao su chống trượt\n"
                + "• Màu: Đen, Trắng\n• Cỡ: 40, 41\nChọn màu, cỡ phù hợp hoặc nhắn cửa hàng để được tư vấn thêm.", out.description());
    }

    @Test
    void theModelWritesAndMarkdownIsCleanedUp() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.text("## Mô tả\n**Giày chạy bộ Elsa** êm chân cho buổi chạy sáng.\n- Màu đen, trắng\n- Cỡ 40, 41");
            ProductDescriptionResponse out = writer(stub.client(), store(1), null)
                    .write(new ProductDescriptionRequest("Giày chạy bộ Elsa", 3L, null, null, null));
            assertTrue(out.ai());
            assertNull(out.notice());
            assertEquals("Mô tả\nGiày chạy bộ Elsa êm chân cho buổi chạy sáng.\n• Màu đen, trắng\n• Cỡ 40, 41", out.description());
            String request = stub.requests.get(0).toString();
            assertTrue(request.contains("Tên: Giày chạy bộ Elsa"), request);
            assertTrue(request.contains("Không bịa chất liệu"), request);
        }
    }

    @Test
    void aFailingOrEmptyAnswerFallsBackToTheTemplate() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.text("ok");
            ProductDescriptionResponse out = writer(stub.client(), store(1), null)
                    .write(new ProductDescriptionRequest("Túi tote", null, null, null, null));
            assertFalse(out.ai());
            assertTrue(out.notice().contains("đang bận"));
            assertEquals("Túi tote — lựa chọn dễ phối cho nhiều dịp.\nChọn màu, cỡ phù hợp hoặc nhắn cửa hàng để được tư vấn thêm.",
                    out.description());
        }
    }

    @Test
    void cleanStripsQuotesAndCapsTheLength() {
        assertEquals("Áo đẹp", ProductCopywriter.clean("\"Áo đẹp\""));
        assertEquals(ProductCopywriter.MAX_CHARS, ProductCopywriter.clean("a".repeat(5000)).length());
    }
}
