
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    private Long customerId;
    private Long userId;
    private Long addressId;
    private Long paymentMethod;

    @NotNull
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
