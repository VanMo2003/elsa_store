package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** Phiếu nhập; {@code items} chỉ có ở màn chi tiết (danh sách để null). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderResponse {
    private Long id;
    private String code;
    /** 0 nháp, 1 đã nhập kho, 2 đã huỷ. */
    private Integer status;
    /** DRAFT, RECEIVED hoặc CANCELLED. */
    private String statusName;
    private Long supplierId;
    private String supplierName;
    private String note;
    private Double totalAmount;
    private Integer totalQuantity;
    private Integer itemCount;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime receivedAt;
    private List<Line> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Line {
        private Long id;
        private Long productVariantId;
        private Long productId;
        private String productName;
        private String color;
        private String size;
        private String sku;
        private String imageUrl;
        private Integer quantity;
        private Double unitPrice;
        private Double lineTotal;
        /** Tồn kho hiện tại; null = biến thể chưa theo dõi kho. */
        private Integer currentStock;
        /** Giá vốn bình quân hiện tại. */
        private Double costPrice;
    }
}
