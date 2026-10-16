package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.ProductViewDayRepository;
import com.example.elsa_store.security.AccessGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Đếm lượt xem trang sản phẩm theo ngày; người của cửa hàng và ADMIN không tính; lỗi đếm không làm hỏng trang. */
@Slf4j
@Service
public class ProductViewCounter {

    private final ProductViewDayRepository repository;
    private final AccessGuard accessGuard;
    private final Clock clock = Clock.systemDefaultZone();

    public ProductViewCounter(ProductViewDayRepository repository, AccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    /** Người đang xem có được tính không (khách / chưa đăng nhập). */
    boolean counts() {
        User viewer = accessGuard.currentUserOrEmpty().orElse(null);
        return viewer == null || viewer.getRole() == Role.USER;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long productId, Long storeId) {
        if (productId == null || !counts()) {
            return;
        }
        try {
            repository.increment(productId, storeId, LocalDate.now(clock));
        } catch (RuntimeException e) {
            log.warn("Cannot record a view for product {}", productId, e);
        }
    }
}
