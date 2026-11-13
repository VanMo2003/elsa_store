package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.dto.response.CustomerResponse;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import com.example.elsa_store.mapper.CustomerMapper;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phân nhóm khách của cửa hàng (VIP, thân thiết, mới, sắp rời bỏ, đã lâu không mua — xem {@link CustomerSegment}) từ các đơn
 * đã hoàn thành ở chính cửa hàng đó, để chủ / quản lý biết nên chăm sóc ai. Chỉ chủ / quản lý xem được vì có số tiền chi.
 */
@Service
@RequiredArgsConstructor
public class CustomerSegments {

    private final AccessGuard accessGuard;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerSegmentsResponse list(CustomerSegment filter) {
        Store store = accessGuard.requireStoreManager();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = orderRepository.completedStatsByCustomer(store.getId());
        Map<Long, Customer> customers = new HashMap<>();
        for (Customer c : customerRepository.findAllById(rows.stream().map(r -> (Long) r[0]).toList())) {
            customers.put(c.getId(), c);
        }
        Map<CustomerSegment, Long> counts = new EnumMap<>(CustomerSegment.class);
        for (CustomerSegment s : CustomerSegment.values()) {
            counts.put(s, 0L);
        }
        List<CustomerSegmentsResponse.Item> items = new ArrayList<>();
        for (Object[] row : rows) {
            Customer c = customers.get((Long) row[0]);
            if (c == null) {
                continue;
            }
            long orders = ((Number) row[1]).longValue();
            double spent = ((Number) row[2]).doubleValue();
            LocalDateTime last = (LocalDateTime) row[3];
            long days = last == null ? Long.MAX_VALUE : Math.max(0, Duration.between(last, now).toDays());
            CustomerSegment segment = CustomerSegment.of(orders, spent, days);
            counts.merge(segment, 1L, Long::sum);
            if (filter == null || filter == segment) {
                // Khách có tài khoản lấy tên / SĐT / email từ tài khoản (cùng quy tắc với danh sách khách).
                CustomerResponse profile = CustomerMapper.toResponse(c);
                items.add(new CustomerSegmentsResponse.Item(c.getId(), profile.getUserId(), profile.getFullName(),
                        profile.getPhone(), profile.getEmail(), orders, Math.round(spent), last,
                        last == null ? 0 : days, segment));
            }
        }
        items.sort(Comparator.comparingDouble(CustomerSegmentsResponse.Item::spent).reversed()
                .thenComparing(CustomerSegmentsResponse.Item::customerId));
        return new CustomerSegmentsResponse(counts, items);
    }
}
