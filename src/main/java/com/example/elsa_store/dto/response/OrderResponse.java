
package com.example.elsa_store.dto.response;


import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Long id;
    private String code;
    private LocalDateTime orderDate;
    private Double totalAmount;
    private Double finalAmount;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private List<OrderItemResponse> items;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }

    public LocalDateTime getOrderDate() { return orderDate; }

    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public Double getTotalAmount() { return totalAmount; }

    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Double getFinalAmount() { return finalAmount; }

    public void setFinalAmount(Double finalAmount) { this.finalAmount = finalAmount; }

    public OrderStatus getStatus() { return status; }

    public void setStatus(OrderStatus status) { this.status = status; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }

    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public List<OrderItemResponse> getItems() { return items; }

    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public static class OrderItemResponse {
        private Long productVariantId;
        private String productName;
        private String pathImage;
        private Integer quantity;
        private Double unitPrice;
        private Double lineTotal;

        public Long getProductVariantId() { return productVariantId; }

        public void setProductVariantId(Long productVariantId) { this.productVariantId = productVariantId; }

        public String getProductName() { return productName; }

        public void setProductName(String productName) { this.productName = productName; }

        public String getPathImage() { return pathImage; }

        public void setPathImage(String pathImage) { this.pathImage = pathImage; }

        public Integer getQuantity() { return quantity; }

        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Double getUnitPrice() { return unitPrice; }

        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Double getLineTotal() { return lineTotal; }

        public void setLineTotal(Double lineTotal) { this.lineTotal = lineTotal; }
    }
}
