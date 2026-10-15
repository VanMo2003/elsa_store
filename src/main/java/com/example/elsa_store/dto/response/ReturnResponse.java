package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ReturnStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ReturnResponse {
    private Long id;
    private String code;
    private Long orderId;
    private String orderCode;
    private Long storeId;
    private String storeName;
    private String customerName;
    private String customerPhone;
    private ReturnStatus status;
    private String reason;
    private String note;
    private String storeNote;
    private Double suggestedRefund;
    private Double refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    /** Người duyệt / từ chối; khách không thấy tên nhân viên. */
    private String decidedByName;
    private LocalDateTime refundedAt;
    private List<Item> items;
    /** Việc người đang xem làm được: {@code CANCEL} (khách), {@code APPROVE}, {@code REJECT}, {@code REFUND}. */
    private List<String> allowedActions;

    @Getter
    @Setter
    public static class Item {
        private Long productVariantId;
        private Long productId;
        private String productName;
        private String color;
        private String size;
        private String imageUrl;
        private Integer quantity;
        private Double unitRefund;
        private Double lineRefund;
    }
}
