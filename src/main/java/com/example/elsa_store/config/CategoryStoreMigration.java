package com.example.elsa_store.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển danh mục chung cũ sang danh mục riêng của từng cửa hàng: với mỗi cửa hàng có sản phẩm nằm trong danh mục chưa
 * thuộc cửa hàng nào ({@code categories.store_id} trống), tạo (hoặc dùng lại theo tên) bản sao của danh mục đó — kèm danh
 * mục cha — trong cửa hàng rồi gắn lại sản phẩm. Danh mục chung cũ vẫn giữ (banner cũ còn trỏ tới; lọc sản phẩm khớp theo
 * tên nên vẫn ra đúng). Chạy sau {@link LegacyDataMigration}; không làm gì khi đã chuyển xong.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class CategoryStoreMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            migrate();
        } catch (Exception e) {
            log.error("Cannot move shared categories into stores", e);
        }
    }

    int migrate() {
        List<Map<String, Object>> pairs = jdbc.queryForList("""
                select distinct p.store_id as store_id, p.category_id as category_id
                from products p join categories c on c.id = p.category_id
                where c.store_id is null and p.store_id is not null
                """);
        if (pairs.isEmpty()) {
            return 0;
        }
        Map<String, Long> copies = new HashMap<>();
        int moved = 0;
        for (Map<String, Object> row : pairs) {
            long storeId = ((Number) row.get("store_id")).longValue();
            long legacyId = ((Number) row.get("category_id")).longValue();
            long copy = copyInto(storeId, legacyId, copies);
            moved += jdbc.update("update products set category_id = ? where store_id = ? and category_id = ?",
                    copy, storeId, legacyId);
        }
        log.warn("Moved {} product(s) from shared categories into per-store categories", moved);
        return moved;
    }

    /** Id bản sao của danh mục {@code legacyId} trong cửa hàng (tạo nếu chưa có cùng tên, cùng cha). */
    private long copyInto(long storeId, long legacyId, Map<String, Long> copies) {
        String cacheKey = storeId + ":" + legacyId;
        Long cached = copies.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> legacy = jdbc.queryForMap("select name, parent_id from categories where id = ?", legacyId);
        String name = (String) legacy.get("name");
        Long parentCopy = legacy.get("parent_id") == null
                ? null
                : copyInto(storeId, ((Number) legacy.get("parent_id")).longValue(), copies);
        List<Long> existing = parentCopy == null
                ? jdbc.queryForList("select id from categories where store_id = ? and parent_id is null and lower(name) = lower(?)",
                        Long.class, storeId, name)
                : jdbc.queryForList("select id from categories where store_id = ? and parent_id = ? and lower(name) = lower(?)",
                        Long.class, storeId, parentCopy, name);
        long id;
        if (!existing.isEmpty()) {
            id = existing.getFirst();
        } else {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "insert into categories (name, parent_id, store_id, created_at, updated_at) values (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                if (parentCopy == null) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, parentCopy);
                }
                ps.setLong(3, storeId);
                ps.setTimestamp(4, now);
                ps.setTimestamp(5, now);
                return ps;
            }, keys);
            id = keys.getKey().longValue();
        }
        copies.put(cacheKey, id);
        return id;
    }
}
