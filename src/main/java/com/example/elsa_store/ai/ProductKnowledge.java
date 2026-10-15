package com.example.elsa_store.ai;

import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.utils.NotificationMessages;
import com.example.elsa_store.utils.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dữ liệu thật mà trợ lý AI được phép dựa vào: hồ sơ cửa hàng (tên, địa chỉ, danh mục đang bán, khoảng giá) và kết quả
 * tìm sản phẩm qua công cụ {@code search_products}. Trợ lý không được tự bịa giá, mẫu mã hay tồn kho — mọi con số nó
 * nói ra đều lấy từ đây. Việc chấm điểm khớp là mã Java thuần ({@link #rank}), mô hình chỉ đưa ra từ khoá.
 *
 * <p>Phạm vi: {@code storeId} khác null = một cửa hàng (chat); null = mọi cửa hàng đã duyệt (trợ lý mua sắm Elsa).
 * Mọi hàm đọc quan hệ lazy nên chạy trong transaction chỉ đọc, kể cả khi gọi từ luồng nền của trợ lý.
 */
@Service
@RequiredArgsConstructor
public class ProductKnowledge {

    /** Số sản phẩm tối đa xét mỗi lần tìm — đủ cho quy mô cửa hàng thời trang, tránh quét vô hạn. */
    private static final int MAX_SCAN = 600;

    private static final int MAX_SEARCH_RESULTS = 8;
    private static final int MAX_LISTED_CATEGORIES = 15;
    private static final int MAX_LISTED_STORES = 10;
    private static final int MAX_OPTIONS_SHOWN = 6;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Từ nối hay gặp trong câu hỏi, không giúp phân biệt sản phẩm. */
    private static final Set<String> STOP_WORDS = Set.of(
            "va", "cua", "cho", "la", "co", "de", "mot", "nhung", "cac", "voi", "nay", "do", "can", "muon", "tim",
            "mua", "minh", "toi", "shop", "ban", "xin", "giup", "goi", "y", "san", "pham", "nao", "khong", "ko");

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;

    // ------------------------------------------------------------------ kiểu dữ liệu

    /** Bộ lọc mô hình rút ra từ câu khách hỏi. */
    public record Filters(
            List<String> keywords, String category, Double minPrice, Double maxPrice, String color, String size) {

        public static Filters of(List<String> keywords) {
            return new Filters(keywords, null, null, null, null, null);
        }
    }

    /** Một mẫu mã (màu / cỡ) của sản phẩm; {@code quantity} trống = chưa cập nhật tồn kho. */
    public record Option(String color, String size, Integer quantity) {}

    /** Sản phẩm ở dạng phẳng, tách khỏi entity để chấm điểm và kiểm thử không cần CSDL. */
    public record Item(
            Long id,
            String name,
            String storeName,
            String category,
            String parentCategory,
            String description,
            Double minPrice,
            Double maxPrice,
            List<Option> options) {

        /** Hết hàng khi mọi mẫu mã đều có tồn kho và đều bằng 0. */
        public Stock stock() {
            if (options.isEmpty() || options.stream().allMatch(o -> o.quantity() == null)) {
                return Stock.UNKNOWN;
            }
            return options.stream().anyMatch(o -> o.quantity() != null && o.quantity() > 0) ? Stock.IN : Stock.OUT;
        }
    }

    public enum Stock {
        IN("còn hàng"),
        OUT("hết hàng"),
        UNKNOWN("chưa cập nhật tồn kho");

        public final String label;

        Stock(String label) {
            this.label = label;
        }
    }

    public record Scored(Item item, int score) {}

    // ------------------------------------------------------------------ hồ sơ

    /** Bản tóm tắt cửa hàng (hoặc toàn sàn) đưa vào lời nhắc hệ thống; giống nhau mọi lượt nên được cache. */
    @Transactional(readOnly = true)
    public String factSheet(Long storeId) {
        List<Item> items = items(storeId);
        StringBuilder out = new StringBuilder();
        if (storeId == null) {
            out.append("HỒ SƠ ELSA STORE (sàn bán thời trang nhiều cửa hàng)\n");
            Map<String, Integer> byStore = new TreeMap<>();
            items.forEach(i -> byStore.merge(i.storeName() == null ? "(chưa rõ)" : i.storeName(), 1, Integer::sum));
            out.append("Cửa hàng đang bán (số sản phẩm):\n");
            byStore.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(MAX_LISTED_STORES)
                    .forEach(e -> out.append("- ")
                            .append(e.getKey())
                            .append(" (")
                            .append(e.getValue())
                            .append(")\n"));
        } else {
            Store store = storeRepository.findById(storeId).orElse(null);
            out.append("HỒ SƠ CỬA HÀNG\n");
            if (store != null) {
                out.append("Tên: ").append(store.getName()).append('\n');
                line(out, "Địa chỉ", store.getAddress());
                line(out, "Điện thoại", store.getPhone());
                line(out, "Giới thiệu", store.getDescription());
            }
        }
        out.append("Danh mục đang bán (số sản phẩm, khoảng giá):\n");
        Map<String, List<Item>> byCategory = new LinkedHashMap<>();
        items.forEach(i -> byCategory
                .computeIfAbsent(i.category() == null ? "Khác" : i.category(), k -> new ArrayList<>())
                .add(i));
        byCategory.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(MAX_LISTED_CATEGORIES)
                .forEach(e -> {
                    double low = e.getValue().stream()
                            .map(Item::minPrice)
                            .filter(java.util.Objects::nonNull)
                            .min(Double::compare)
                            .orElse(0.0);
                    double high = e.getValue().stream()
                            .map(Item::maxPrice)
                            .filter(java.util.Objects::nonNull)
                            .max(Double::compare)
                            .orElse(0.0);
                    out.append("- ")
                            .append(e.getKey())
                            .append(" (")
                            .append(e.getValue().size())
                            .append(" sản phẩm, ")
                            .append(priceRange(low, high))
                            .append(")\n");
                });
        out.append("Tổng cộng ").append(items.size()).append(" sản phẩm đang bán.\n");
        return out.toString();
    }

    /** Vài đơn gần đây của khách tại cửa hàng, để trả lời "đơn của tôi xác nhận chưa". */
    @Transactional(readOnly = true)
    public String recentOrders(Long storeId, Long userId) {
        List<Order> orders = orderRepository.findAllByStore_IdAndUser_Id(storeId, userId).stream()
                .sorted(Comparator.comparing(Order::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();
        if (orders.isEmpty()) {
            return "- (chưa có)\n";
        }
        StringBuilder out = new StringBuilder();
        for (Order order : orders) {
            out.append("- ").append(order.getCode());
            if (order.getOrderDate() != null) {
                out.append(" · ").append(order.getOrderDate().format(DAY));
            }
            out.append(" · ").append(NotificationMessages.money(order.getFinalAmount()));
            out.append(" · ").append(statusLabel(order));
            String names = order.getItems() == null
                    ? ""
                    : order.getItems().stream()
                            .map(OrderItem::getProductVariant)
                            .map(v -> v.getProduct().getName())
                            .distinct()
                            .limit(2)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
            if (!names.isEmpty()) {
                out.append(" · ").append(names);
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String statusLabel(Order order) {
        if (order.getStatus() == null) {
            return "chưa rõ";
        }
        return switch (order.getStatus()) {
            case CHUA_XAC_NHAN -> "CHỜ CỬA HÀNG XÁC NHẬN";
            case DA_XAC_NHAN -> "ĐÃ XÁC NHẬN";
            case HOAN_THANH -> "HOÀN THÀNH";
            case DA_HUY -> "ĐÃ HUỶ";
        };
    }

    // ------------------------------------------------------------------ tìm sản phẩm

    /** Tìm sản phẩm đang bán theo bộ lọc; trả về tối đa {@code limit} sản phẩm khớp nhất. */
    @Transactional(readOnly = true)
    public List<Scored> search(Long storeId, Filters filters, int limit) {
        return rank(items(storeId), filters, Math.max(1, Math.min(limit, MAX_SEARCH_RESULTS)));
    }

    /**
     * Chấm điểm và xếp hạng. Giá là ràng buộc cứng (sản phẩm phải có mức giá nằm trong khoảng khách nói); từ khoá, màu,
     * cỡ chỉ cộng điểm. Có từ khoá mà không từ nào khớp thì sản phẩm bị loại; không có từ khoá thì liệt kê theo thứ tự
     * mới nhất. Còn hàng được ưu tiên hơn hết hàng.
     */
    public static List<Scored> rank(List<Item> items, Filters filters, int limit) {
        List<String> tokens = tokens(filters.keywords());
        String color = TextNormalizer.normalize(filters.color());
        String size = TextNormalizer.normalize(filters.size());
        String category = TextNormalizer.normalize(filters.category());
        boolean categoryKnown = category.isEmpty()
                || items.stream().anyMatch(i -> categoryMatches(i, category));

        List<Scored> scored = new ArrayList<>();
        for (Item item : items) {
            if (filters.maxPrice() != null && item.minPrice() != null && item.minPrice() > filters.maxPrice()) {
                continue;
            }
            if (filters.minPrice() != null && item.maxPrice() != null && item.maxPrice() < filters.minPrice()) {
                continue;
            }
            // Danh mục mô hình nêu không tồn tại thì bỏ điều kiện này thay vì trả về rỗng.
            if (!category.isEmpty() && categoryKnown && !categoryMatches(item, category)) {
                continue;
            }
            int score = 0;
            String name = TextNormalizer.normalize(item.name());
            String cat = TextNormalizer.normalize(nz(item.category()) + " " + nz(item.parentCategory()));
            String rest = TextNormalizer.normalize(
                    nz(item.description()) + " " + nz(item.storeName()) + " " + options(item));
            for (String token : tokens) {
                if (hasWord(name, token)) {
                    score += 3;
                } else if (hasWord(cat, token)) {
                    score += 2;
                } else if (hasWord(rest, token)) {
                    score += 1;
                }
            }
            if (!tokens.isEmpty() && score == 0) {
                continue;
            }
            if (!color.isEmpty()
                    && item.options().stream().anyMatch(o -> hasWord(TextNormalizer.normalize(o.color()), color))) {
                score += 4;
            }
            if (!size.isEmpty()
                    && item.options().stream().anyMatch(o -> TextNormalizer.normalize(o.size()).equals(size))) {
                score += 4;
            }
            scored.add(new Scored(item, score));
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.score())
                .thenComparing(s -> s.item().stock() == Stock.OUT ? 1 : 0)
                .thenComparing(s -> -s.item().id()));
        return scored.stream().limit(limit).toList();
    }

    /** Từ khoá đã bỏ dấu, tách từ, bỏ từ nối và từ quá ngắn; trùng lặp được gộp. */
    public static List<String> tokens(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .flatMap(k -> java.util.Arrays.stream(TextNormalizer.normalize(k).split(" ")))
                .filter(t -> t.length() >= 2 && !STOP_WORDS.contains(t))
                .distinct()
                .toList();
    }

    /**
     * {@code token} là phần đầu của một từ trong {@code haystack} (đều đã bỏ dấu): "ao" khớp "ao thun" nhưng không khớp
     * "thao", còn "chay" vẫn khớp "chay bo" và "giay" khớp "giay".
     */
    static boolean hasWord(String haystack, String token) {
        return !token.isEmpty() && (" " + haystack).contains(" " + token);
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }

    private static boolean categoryMatches(Item item, String normalizedCategory) {
        return TextNormalizer.looselyMatches(item.category(), normalizedCategory)
                || TextNormalizer.looselyMatches(item.parentCategory(), normalizedCategory);
    }

    private static String options(Item item) {
        StringBuilder out = new StringBuilder();
        for (Option o : item.options()) {
            out.append(nz(o.color())).append(' ').append(nz(o.size())).append(' ');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ chuyển đổi

    private List<Item> items(Long storeId) {
        return productRepository.findPublic(null, storeId).stream()
                .limit(MAX_SCAN)
                .map(ProductKnowledge::toItem)
                .toList();
    }

    static Item toItem(Product product) {
        List<Option> options = new ArrayList<>();
        Double low = null;
        Double high = null;
        if (product.getVariants() != null) {
            for (ProductVariant v : product.getVariants()) {
                if (v.getStatus() != null && v.getStatus() != 1) {
                    continue;
                }
                options.add(new Option(
                        v.getColor(),
                        v.getSize(),
                        v.getInventory() == null ? null : v.getInventory().getQuantity()));
                if (v.getPrice() != null) {
                    low = low == null ? v.getPrice() : Math.min(low, v.getPrice());
                    high = high == null ? v.getPrice() : Math.max(high, v.getPrice());
                }
            }
        }
        if (low == null) {
            low = product.getBasePrice();
            high = product.getBasePrice();
        }
        Category category = product.getCategory();
        return new Item(
                product.getId(),
                product.getName(),
                product.getStore() == null ? null : product.getStore().getName(),
                category == null ? null : category.getName(),
                category == null || category.getParent() == null
                        ? null
                        : category.getParent().getName(),
                product.getDescription(),
                low,
                high,
                options);
    }

    // ------------------------------------------------------------------ định dạng cho mô hình

    /** Mô tả ngắn các mẫu mã còn/hết, ví dụ "Trắng 42 (còn 5); Đen 41 (hết)". */
    public static String optionsSummary(Item item) {
        List<String> parts = new ArrayList<>();
        for (Option o : item.options()) {
            if (parts.size() >= MAX_OPTIONS_SHOWN) {
                parts.add("…");
                break;
            }
            String label = ((o.color() == null ? "" : o.color()) + " " + (o.size() == null ? "" : o.size())).strip();
            if (label.isEmpty()) {
                continue;
            }
            String stock = o.quantity() == null ? "" : o.quantity() > 0 ? " (còn " + o.quantity() + ")" : " (hết)";
            parts.add(label + stock);
        }
        return String.join("; ", parts);
    }

    public static String priceRange(Double low, Double high) {
        if (low == null) {
            return "chưa có giá";
        }
        if (high == null || high.equals(low)) {
            return NotificationMessages.money(low);
        }
        return NotificationMessages.money(low) + " – " + NotificationMessages.money(high);
    }

    private static void line(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(value.strip()).append('\n');
        }
    }
}
