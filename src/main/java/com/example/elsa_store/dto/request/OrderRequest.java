package com.example.elsa_store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Đặt đơn (khách) hoặc lập đơn tại quầy (nhân sự cửa hàng). Cũng dùng cho {@code POST /orders/quote} (chỉ cần
 * {@code items} và {@code couponCode}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    private Long customerId;
    private Long userId;

    /** Địa chỉ trong sổ địa chỉ của khách; hoặc gửi trực tiếp người nhận / SĐT / địa chỉ bên dưới. */
    private Long addressId;

    /** 0 hoặc trống = thanh toán khi nhận hàng (COD); 1 = VNPay. */
    private Long paymentMethod;

    @Size(max = 100)
    private String receiverName;

    @Size(max = 20)
    private String receiverPhone;

    @Size(max = 500)
    private String shippingAddress;

    @Size(max = 255)
    private String note;

    @Size(max = 50)
    private String couponCode;

    /** Đặt từ giỏ hàng: các món đã đặt được bỏ khỏi giỏ. */
    private Boolean fromCart;

    @NotNull
    @Valid
    private List<OrderItemRequest> items;

    public static class OrderItemRequest {
        @NotNull
        private Long productVariantId;

        @NotNull
        private Integer quantity;

        public Long getProductVariantId() { return productVariantId; }

        public void setProductVariantId(Long productVariantId) { this.productVariantId = productVariantId; }

        public Integer getQuantity() { return quantity; }

        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
