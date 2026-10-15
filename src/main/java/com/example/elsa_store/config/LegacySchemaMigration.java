package com.example.elsa_store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Đưa các cột của CSDL tạo trước khi có phân quyền theo cửa hàng về đúng kiểu, <b>trước</b> khi Hibernate cập nhật
 * lược đồ ({@code ddl-auto=update}). Thứ tự này bắt buộc: nếu để Hibernate đổi trước, cột {@code users.role} kiểu số
 * bị đổi sang kiểu enum của MySQL theo <i>chỉ số</i> (0 → chuỗi rỗng, 1 → ADMIN, 3 → STORE_MANAGER…) và phân quyền
 * của mọi tài khoản cũ bị đảo lộn.
 *
 * <ol>
 *   <li>{@code users.role}: số thứ tự enum cũ (ADMIN, STAFF, WAREHOUSE, CUSTOMER) → tên vai trò mới, bỏ ràng buộc
 *       {@code check} kiểu số của Hibernate;</li>
 *   <li>{@code customers.user_id}: cho phép null (khách vãng lai do nhân viên tạo tại quầy);</li>
 *   <li>{@code orders.status}, {@code notifications.type}: kiểu {@code enum} của MySQL → chuỗi, để thêm được trạng
 *       thái / loại thông báo mới.</li>
 * </ol>
 *
 * Mỗi bước tự kiểm tra và bỏ qua khi CSDL đã mới hoặc chưa có bảng (chạy trên CSDL trống là không làm gì).
 * Việc gom dữ liệu chưa thuộc cửa hàng nào cần bảng mới nên nằm ở {@link LegacyDataMigration}.
 */
@Slf4j
@Component("legacySchemaMigration")
public class LegacySchemaMigration implements InitializingBean {

    private final JdbcTemplate jdbc;

    public LegacySchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            migrateRoleColumn();
            allowCustomersWithoutAccount();
        } catch (Exception e) {
            log.error("Legacy schema migration failed - check users.role and customers.user_id manually", e);
        }
        try {
            enumColumnToVarchar("orders", "status", "varchar(30) null");
            enumColumnToVarchar("notifications", "type", "varchar(40) not null");
            for (String[] column : ENUM_TEXT_COLUMNS) {
                dropEnumValueChecks(column[0], column[1]);
            }
        } catch (Exception e) {
            log.error("Cannot convert orders.status / notifications.type to varchar - new statuses will not save", e);
        }
    }

    /** Cột enum lưu dạng chuỗi ({@code @JdbcTypeCode(VARCHAR)}) mà ứng dụng sẽ còn thêm giá trị mới. */
    private static final List<String[]> ENUM_TEXT_COLUMNS = List.of(
            new String[] {"orders", "status"},
            new String[] {"notifications", "type"},
            new String[] {"order_status_history", "status"},
            new String[] {"return_requests", "status"},
            new String[] {"coin_transactions", "type"});

    /**
     * Trên CSDL mới, Hibernate tạo kèm {@code check (status in ('A','B'…))} với đúng các giá trị lúc tạo bảng; giá trị thêm
     * sau đó (loại thông báo, trạng thái mới) bị MySQL từ chối (lỗi 3819). Bước này chạy mỗi lần khởi động, trước
     * Hibernate, nên bản nâng cấp nào thêm giá trị mới cũng lưu được.
     */
    private void dropEnumValueChecks(String table, String column) {
        List<String> names = jdbc.queryForList(
                """
                select tc.constraint_name from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
                where tc.table_schema = database() and tc.table_name = ? and tc.constraint_type = 'CHECK'
                  and cc.check_clause like ? and lower(cc.check_clause) like '% in (%'
                """,
                String.class, table, "%`" + column + "`%");
        for (String name : names) {
            log.warn("Dropping enum value check {}.{} ({})", table, name, column);
            jdbc.execute("alter table `" + table + "` drop check `" + name.replace("`", "``") + "`");
        }
    }

    /**
     * Hibernate 6 tạo cột {@code @Enumerated(STRING)} thành kiểu {@code enum(...)} của MySQL với đúng các giá trị lúc
     * tạo bảng, và {@code ddl-auto=update} không bao giờ sửa lại — thêm trạng thái mới (DANG_GIAO, ORDER_SHIPPING) sẽ
     * bị MySQL từ chối khi lưu. Đổi cột sang chuỗi giữ nguyên dữ liệu; entity cũng đã khai báo kiểu chuỗi cho CSDL mới.
     */
    private void enumColumnToVarchar(String table, String column, String definition) {
        if (!"enum".equalsIgnoreCase(columnInfo(table, column, "data_type"))) {
            return;
        }
        log.warn("{}.{} is a MySQL enum - converting to {}", table, column, definition);
        jdbc.execute("alter table " + table + " modify " + column + " " + definition);
    }

    /**
     * Chịu được cả hai trạng thái của CSDL cũ: cột còn kiểu số ({@code tinyint}…) và cột đã bị đổi dở sang chuỗi nhưng
     * vẫn chứa số thứ tự ({@code '0'}, {@code '3'}…). Hibernate 6 tạo cột số này kèm ràng buộc
     * {@code check (role between 0 and 3)}; ràng buộc phải bỏ trước khi ghi tên vai trò, nếu không câu cập nhật bị chặn
     * (lỗi 3819) và ứng dụng không khởi động được.
     */
    private void migrateRoleColumn() {
        String type = columnInfo("users", "role", "data_type");
        if (type == null) {
            return;
        }
        String kind = type.toLowerCase();
        boolean numeric = List.of("int", "tinyint", "smallint", "bigint", "mediumint").contains(kind);
        boolean text = List.of("varchar", "char", "text").contains(kind);
        if (!numeric && !text) {
            // enum của MySQL: Hibernate đã tự tạo theo kiểu mới, không còn gì để chuyển.
            return;
        }
        if (text && !hasLegacyRoleValues()) {
            return;
        }
        log.warn("users.role holds legacy ordinals ({}) - converting to role names", type);
        dropRoleCheckConstraints();
        if (numeric) {
            jdbc.execute("alter table users modify role varchar(30)");
        }
        jdbc.update("""
                update users set role = case role
                    when '0' then 'ADMIN'
                    when '1' then 'STAFF'
                    when '2' then 'STAFF'
                    else 'USER' end
                where role in ('0','1','2','3') or role is null
                """);
    }

    private boolean hasLegacyRoleValues() {
        Integer count = jdbc.queryForObject(
                "select count(*) from users where role in ('0','1','2','3') or role is null", Integer.class);
        return count != null && count > 0;
    }

    /** Bỏ mọi ràng buộc CHECK của bảng {@code users} có nhắc tới cột {@code role} (tên do Hibernate đặt, ví dụ users_chk_1). */
    private void dropRoleCheckConstraints() {
        List<String> names = jdbc.queryForList(
                """
                select tc.constraint_name from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
                where tc.table_schema = database() and tc.table_name = 'users'
                  and tc.constraint_type = 'CHECK' and lower(cc.check_clause) like '%role%'
                """,
                String.class);
        for (String name : names) {
            log.warn("Dropping legacy check constraint users.{}", name);
            jdbc.execute("alter table users drop check `" + name.replace("`", "``") + "`");
        }
    }

    private void allowCustomersWithoutAccount() {
        if ("NO".equalsIgnoreCase(columnInfo("customers", "user_id", "is_nullable"))) {
            log.warn("customers.user_id is NOT NULL - relaxing it for walk-in customers");
            jdbc.execute("alter table customers modify user_id bigint null");
        }
    }

    private String columnInfo(String table, String column, String field) {
        List<String> rows = jdbc.queryForList(
                "select " + field + " from information_schema.columns"
                        + " where table_schema = database() and table_name = ? and column_name = ?",
                String.class, table, column);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Bắt EntityManagerFactory (Hibernate) chờ bước chuyển đổi này chạy xong rồi mới cập nhật lược đồ. */
    @Component
    static class RunBeforeHibernate extends EntityManagerFactoryDependsOnPostProcessor {
        RunBeforeHibernate() {
            super("legacySchemaMigration");
        }
    }
}
