package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thẻ sản phẩm đính kèm tin nhắn. Dùng chung cho chat và trợ lý AI nên tách riêng; mọi hàm đọc quan hệ lazy của sản
 * phẩm nên chạy trong transaction chỉ đọc (kể cả khi được gọi từ luồng nền của trợ lý).
 */
@Component
@RequiredArgsConstructor
public class ChatProducts {

    /** Số sản phẩm tối đa đính kèm một tin. */
    public static final int MAX_PER_MESSAGE = 3;

    private final ProductRepository productRepository;

    /** Thẻ của các sản phẩm theo mã, giữ thứ tự {@code ids}; mã không còn trong CSDL bị bỏ qua. */
    @Transactional(readOnly = true)
    public Map<Long, ChatProductResponse> load(Collection<Long> ids) {
        Map<Long, ChatProductResponse> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        Map<Long, Product> byId = new LinkedHashMap<>();
        productRepository.findAllById(ids).forEach(p -> byId.put(p.getId(), p));
        for (Long id : ids) {
            Product product = byId.get(id);
            if (product != null) {
                out.put(id, card(product));
            }
        }
        return out;
    }

    /**
     * Mã sản phẩm hợp lệ để đính kèm tin của cuộc trò chuyện với cửa hàng {@code storeId}: có thật, thuộc cửa hàng đó
     * và đang bán. Trùng lặp bị gộp, tối đa {@value #MAX_PER_MESSAGE}.
     *
     * @return danh sách mã đã lọc, hoặc ném {@link IllegalArgumentException} nếu có mã không dùng được
     */
    @Transactional(readOnly = true)
    public List<Long> requireAttachable(List<Long> ids, Long storeId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_PER_MESSAGE).toList();
        Map<Long, Product> byId = new LinkedHashMap<>();
        productRepository.findAllById(distinct).forEach(p -> byId.put(p.getId(), p));
        for (Long id : distinct) {
            Product product = byId.get(id);
            if (product == null
                    || product.getStore() == null
                    || !product.getStore().getId().equals(storeId)
                    || !onSale(product)) {
                throw new IllegalArgumentException("Product " + id + " cannot be attached to this conversation");
            }
        }
        return distinct;
    }

    public static boolean onSale(Product product) {
        boolean active = product.getStatus() == null || product.getStatus() == 1;
        boolean storeOpen = product.getStore() == null || product.getStore().isOpenToCustomers();
        return active && storeOpen;
    }

    public static ChatProductResponse card(Product product) {
        return ChatProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .price(startingPrice(product))
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .storeId(product.getStore() == null ? null : product.getStore().getId())
                .storeName(product.getStore() == null ? null : product.getStore().getName())
                .available(onSale(product))
                .build();
    }

    /** Giá thấp nhất trong các biến thể đang bán; chưa có biến thể thì lấy giá gốc. */
    public static Double startingPrice(Product product) {
        Double lowest = null;
        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                boolean active = variant.getStatus() == null || variant.getStatus() == 1;
                if (active && variant.getPrice() != null && (lowest == null || variant.getPrice() < lowest)) {
                    lowest = variant.getPrice();
                }
            }
        }
        return lowest != null ? lowest : product.getBasePrice();
    }
}
