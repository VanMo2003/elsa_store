package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private Long id;
    private String code;
    private LocalDateTime orderDate;
    /** Lúc đơn hoàn thành (null khi chưa hoàn thành hoặc đơn cũ). */
    private LocalDateTime completedAt;
    /** Tổng theo giá gốc. */
    private Double totalAmount;
    /** Tổng tiền được giảm (khuyến mãi theo sản phẩm + giảm cả đơn / mã giảm giá). */
    private Double discountAmount;
    /** Số tiền khách trả. */
    private Double finalAmount;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    /** COD hoặc VNPAY. */
    private String paymentMethod;
    private String couponCode;
    private String discountNote;
    private List<OrderItemResponse> items;
    private Long storeId;
    private String storeName;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;
    private String note;
    /** Nhân viên / quản lý lập đơn tại quầy; null khi khách tự đặt. */
    private String handledByName;
    /** Trạng thái mà người đang xem được phép chuyển đơn sang — app chỉ hiện đúng các nút này. */
    private List<OrderStatus> allowedNextStatuses;

    @Getter
    @Setter
    public static class OrderItemResponse {
        private Long productVariantId;
        private Long productId;
        private String productName;
        private String color;
        private String size;
        private String pathImage;
        private Integer quantity;
        /** Đơn giá gốc trước khuyến mãi (trống ở đơn cũ). */
        private Double originalUnitPrice;
        /** Đơn giá khách trả. */
        private Double unitPrice;
        private Double discount;
        private Double lineTotal;
    }
}
