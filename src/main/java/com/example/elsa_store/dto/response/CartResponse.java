package com.example.elsa_store.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long cartId;
    private Long userId;
    private List<CartItemResponse> items;
    private Double totalAmount;
    /** Cửa hàng của các sản phẩm trong giỏ (một giỏ chỉ chứa hàng của một cửa hàng); null khi giỏ trống. */
    private Long storeId;
    private String storeName;
}
