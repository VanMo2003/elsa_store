package com.example.elsa_store.config;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sau khi lược đồ đã đúng ({@link LegacySchemaMigration}): sản phẩm, đơn hàng… tạo trước khi có cửa hàng chưa thuộc
 * cửa hàng nào, nên được gom về một cửa hàng mặc định đã duyệt để không mất dữ liệu và vẫn quản lý được.
 * Không làm gì khi mọi bản ghi đã có cửa hàng (hoặc CSDL trống).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacyDataMigration implements ApplicationRunner {

    static final String DEFAULT_OWNER = "owner123";
    static final String DEFAULT_STORE_NAME = "Elsa Store";

    private final JdbcTemplate jdbc;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            adoptStorelessData();
        } catch (Exception e) {
            log.error("Cannot assign legacy records to the default store", e);
        }
    }

    private void adoptStorelessData() {
        long orphans = count("select count(*) from products where store_id is null")
                + count("select count(*) from users where role in ('STAFF','STORE_MANAGER') and store_id is null");
        if (orphans == 0) {
            return;
        }
        Store store = storeRepository.findAll().stream().findFirst().orElseGet(this::createDefaultStore);
        log.warn("Assigning {} legacy record(s) without a store to '{}'", orphans, store.getName());
        for (String table : List.of("products", "suppliers", "promotions", "purchase_orders", "orders")) {
            jdbc.update("update " + table + " set store_id = ? where store_id is null", store.getId());
        }
        jdbc.update("update users set store_id = ? where role in ('STAFF','STORE_MANAGER') and store_id is null", store.getId());
    }

    private Store createDefaultStore() {
        User owner = userRepository.findByUsername(DEFAULT_OWNER).orElseGet(() -> userRepository.save(User.builder()
                .username(DEFAULT_OWNER)
                .password(passwordEncoder.encode(DEFAULT_OWNER))
                .fullName("Chủ cửa hàng Elsa")
                .role(Role.STORE_OWNER)
                .enabled(true)
                .build()));
        Store store = storeRepository.save(Store.builder()
                .name(DEFAULT_STORE_NAME)
                .ownerName(owner.getFullName())
                .owner(owner)
                .approvalStatus(ApprovalStatus.APPROVED)
                .submittedAt(LocalDateTime.now())
                .reviewedAt(LocalDateTime.now())
                .build());
        log.warn("Created the default store '{}' (owner account {}/{} - please change the password)",
                store.getName(), DEFAULT_OWNER, DEFAULT_OWNER);
        return store;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
