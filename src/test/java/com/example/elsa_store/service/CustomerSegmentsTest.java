package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.example.elsa_store.constant.CustomerSegment.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Phân nhóm khách: quy tắc nhóm, đếm đủ 5 nhóm, lọc, chi nhiều nhất trước, chỉ chủ / quản lý. */
class CustomerSegmentsTest {

    @Test
    void rulesInOrder() {
        assertEquals(LOST, CustomerSegment.of(20, 50_000_000, 181), "quá 180 ngày là đã lâu không mua, kể cả VIP cũ");
        assertEquals(VIP, CustomerSegment.of(1, 5_000_000, 100), "chi từ 5 triệu");
        assertEquals(VIP, CustomerSegment.of(5, 500_000, 10), "từ 5 đơn");
        assertEquals(AT_RISK, CustomerSegment.of(3, 900_000, 61));
        assertEquals(AT_RISK, CustomerSegment.of(1, 300_000, 90), "một đơn, lâu rồi");
        assertEquals(LOYAL, CustomerSegment.of(2, 600_000, 60));
        assertEquals(NEW, CustomerSegment.of(1, 300_000, 3));
    }

    private static Customer customer(long id, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName(name);
        return c;
    }

    private final AccessGuard guard = mock(AccessGuard.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSegments service = new CustomerSegments(guard, orders, customers);

    private void data() {
        Store store = new Store();
        store.setId(1L);
        when(guard.requireStoreManager()).thenReturn(store);
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {1L, 6L, 8_000_000.0, now.minusDays(5)});
        rows.add(new Object[] {2L, 1L, 300_000.0, now.minusDays(2)});
        rows.add(new Object[] {3L, 2L, 1_200_000.0, now.minusDays(75)});
        rows.add(new Object[] {4L, 3L, 900_000.0, now.minusDays(400)});
        rows.add(new Object[] {5L, 1L, 450_000.0, now.minusDays(1)});
        when(orders.completedStatsByCustomer(1L)).thenReturn(rows);
        when(customers.findAllById(anyIterable())).thenReturn(List.of(
                customer(1, "An"), customer(2, "Bình"), customer(3, "Chi"), customer(4, "Dũng"), customer(5, "Em")));
    }

    @Test
    void countsEverySegmentAndSortsBySpending() {
        data();
        CustomerSegmentsResponse all = service.list(null);
        assertEquals(1L, all.counts().get(VIP));
        assertEquals(0L, all.counts().get(LOYAL));
        assertEquals(2L, all.counts().get(NEW));
        assertEquals(1L, all.counts().get(AT_RISK));
        assertEquals(1L, all.counts().get(LOST));
        assertEquals(List.of("An", "Chi", "Dũng", "Em", "Bình"),
                all.customers().stream().map(CustomerSegmentsResponse.Item::fullName).toList());
        CustomerSegmentsResponse.Item chi = all.customers().get(1);
        assertEquals(75, chi.daysSinceLastOrder());
        assertEquals(AT_RISK, chi.segment());
    }

    @Test
    void filterKeepsCountsOfAllSegments() {
        data();
        CustomerSegmentsResponse fresh = service.list(NEW);
        assertEquals(List.of("Em", "Bình"), fresh.customers().stream().map(CustomerSegmentsResponse.Item::fullName).toList());
        assertEquals(5, fresh.counts().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void staffCannotSeeSpending() {
        when(guard.requireStoreManager()).thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.list(null));
    }
}
