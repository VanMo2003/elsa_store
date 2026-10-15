package com.example.elsa_store.ai;

import com.example.elsa_store.ai.ProductKnowledge.Filters;
import com.example.elsa_store.ai.ProductKnowledge.Item;
import com.example.elsa_store.ai.ProductKnowledge.Option;
import com.example.elsa_store.ai.ProductKnowledge.Scored;
import com.example.elsa_store.ai.ProductKnowledge.Stock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chấm điểm khớp sản phẩm — phần "tìm" của trợ lý là mã Java thuần, không phụ thuộc mô hình. */
class ProductKnowledgeTest {

    private static Item item(long id, String name, String category, double low, double high, Option... options) {
        return new Item(id, name, "Store A", category, null, null, low, high, List.of(options));
    }

    private static Option opt(String color, String size, Integer quantity) {
        return new Option(color, size, quantity);
    }

    private static List<Long> ids(List<Scored> scored) {
        return scored.stream().map(s -> s.item().id()).toList();
    }

    private final List<Item> catalog = List.of(
            item(1, "Giày chạy bộ Nike Pegasus", "Giày", 1_800_000, 2_200_000, opt("Đen", "42", 5)),
            item(2, "Giày thể thao Adidas Samba", "Giày", 1_250_000, 1_250_000, opt("Trắng", "42", 3)),
            item(3, "Áo thun cotton basic", "Áo", 150_000, 200_000, opt("Đen", "M", 10), opt("Trắng", "L", 0)),
            item(4, "Áo khoác gió chạy bộ", "Áo", 450_000, 450_000, opt("Xanh", "L", 0)),
            item(5, "Quần short thể thao", "Quần", 250_000, 250_000));

    @Test
    void keywordsMatchWithoutAccents() {
        List<Scored> result = ProductKnowledge.rank(catalog, Filters.of(List.of("giay", "chay bo")), 6);

        assertEquals(1L, result.getFirst().item().id());
        assertTrue(ids(result).contains(2L), "'giay' còn khớp danh mục của giày khác");
    }

    @Test
    void nameMatchesOutrankDescriptionMatches() {
        Item described = new Item(10L, "Túi tote", "Store A", "Túi", null, "hợp mang đi biển", 100_000.0, 100_000.0, List.of());
        Item named = item(11, "Áo đi biển", "Áo", 100_000, 100_000);

        List<Scored> result = ProductKnowledge.rank(List.of(described, named), Filters.of(List.of("bien")), 6);

        assertEquals(List.of(11L, 10L), ids(result));
    }

    @Test
    void maxPriceIsAHardLimit() {
        Filters filters = new Filters(List.of("giay"), null, null, 1_500_000.0, null, null);

        assertEquals(List.of(2L), ids(ProductKnowledge.rank(catalog, filters, 6)));
    }

    @Test
    void minPriceExcludesCheaperProducts() {
        Filters filters = new Filters(List.of(), null, 1_000_000.0, null, null, null);

        assertEquals(Set.of(1L, 2L), Set.copyOf(ids(ProductKnowledge.rank(catalog, filters, 6))));
    }

    @Test
    void shortKeywordsDoNotMatchInsideOtherWords() {
        // "ao" không được khớp "thao" trong "Giày thể thao".
        List<Scored> result = ProductKnowledge.rank(catalog, Filters.of(List.of("ao")), 6);

        assertEquals(Set.of(3L, 4L), Set.copyOf(ids(result)));
    }

    @Test
    void priceRangeOverlapKeepsProductsThatHaveAnAffordableVariant() {
        // Áo thun có biến thể 150k–200k: nằm trong "dưới 180k" dù biến thể đắt nhất vượt ngân sách.
        Filters filters = new Filters(List.of("ao thun"), null, null, 180_000.0, null, null);

        assertEquals(List.of(3L), ids(ProductKnowledge.rank(catalog, filters, 6)));
    }

    @Test
    void colorAndSizeBoostMatchingProducts() {
        Filters filters = new Filters(List.of("ao"), null, null, null, "trang", "L");

        List<Scored> result = ProductKnowledge.rank(catalog, filters, 6);

        // Áo thun có Trắng/L; áo khoác chỉ có Xanh/L nên điểm thấp hơn.
        assertEquals(3L, result.getFirst().item().id());
        assertTrue(result.getFirst().score() > result.get(1).score());
    }

    @Test
    void outOfStockProductsRankAfterAvailableOnesWithTheSameScore() {
        // Hai áo cùng khớp "chay bo" ở cùng mức điểm tên; áo khoác hết hàng phải đứng sau giày còn hàng.
        List<Item> items = List.of(
                item(20, "Đồ chạy bộ A", "Áo", 100_000, 100_000, opt("Đen", "M", 0)),
                item(21, "Đồ chạy bộ B", "Áo", 100_000, 100_000, opt("Đen", "M", 4)));

        assertEquals(List.of(21L, 20L), ids(ProductKnowledge.rank(items, Filters.of(List.of("chay bo")), 6)));
    }

    @Test
    void unknownCategoryIsIgnoredInsteadOfEmptyingTheResult() {
        Filters filters = new Filters(List.of("giay"), "Đồ gia dụng", null, null, null, null);

        assertTrue(ids(ProductKnowledge.rank(catalog, filters, 6)).contains(1L));
    }

    @Test
    void knownCategoryRestrictsTheResult() {
        Filters filters = new Filters(List.of(), "ao", null, null, null, null);

        // Áo khoác (mã 4) hết hàng nên xếp sau áo thun còn hàng dù mới hơn.
        assertEquals(List.of(3L, 4L), ids(ProductKnowledge.rank(catalog, filters, 6)));
    }

    @Test
    void noKeywordsListsNewestFirstAndPushesOutOfStockToTheEnd() {
        assertEquals(List.of(5L, 3L, 2L), ids(ProductKnowledge.rank(catalog, Filters.of(List.of()), 3)));
        assertEquals(4L, ids(ProductKnowledge.rank(catalog, Filters.of(List.of()), 6)).getLast());
    }

    @Test
    void keywordsThatMatchNothingReturnNothing() {
        assertTrue(ProductKnowledge.rank(catalog, Filters.of(List.of("vay da hoi")), 6).isEmpty());
    }

    @Test
    void stopWordsAndShortTokensAreDropped() {
        assertEquals(List.of("giay", "chay", "bo"), ProductKnowledge.tokens(List.of("Cho mình tìm giày", "chạy bộ", "ạ")));
    }

    @Test
    void stockStates() {
        assertEquals(Stock.UNKNOWN, item(30, "A", "x", 1, 1).stock());
        assertEquals(Stock.UNKNOWN, item(31, "A", "x", 1, 1, opt("Đen", "M", null)).stock());
        assertEquals(Stock.IN, item(32, "A", "x", 1, 1, opt("Đen", "M", 0), opt("Đen", "L", 2)).stock());
        assertEquals(Stock.OUT, item(33, "A", "x", 1, 1, opt("Đen", "M", 0), opt("Đen", "L", 0)).stock());
    }

    @Test
    void optionsSummaryShowsStockPerVariant() {
        Item shirt = item(3, "Áo", "Áo", 1, 1, opt("Đen", "M", 10), opt("Trắng", "L", 0), opt("Xám", null, null));

        assertEquals("Đen M (còn 10); Trắng L (hết); Xám", ProductKnowledge.optionsSummary(shirt));
    }

    @Test
    void priceRangeFormatting() {
        assertEquals("1.250.000đ", ProductKnowledge.priceRange(1_250_000.0, 1_250_000.0));
        assertEquals("150.000đ – 200.000đ", ProductKnowledge.priceRange(150_000.0, 200_000.0));
        assertEquals("chưa có giá", ProductKnowledge.priceRange(null, null));
    }
}
