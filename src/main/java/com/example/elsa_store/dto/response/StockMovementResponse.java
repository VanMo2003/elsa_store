package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.StockMovementType;
import lombok.*;

import java.time.LocalDateTime;

/** Một dòng thẻ kho. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {
    private Long id;
    private StockMovementType type;
    private Integer quantityChange;
    private Integer balanceAfter;
    private Double unitCost;
    /** ORDER hoặc PURCHASE_ORDER. */
    private String refType;
    private Long refId;
    private String refCode;
    private String note;
    private String createdByName;
    private LocalDateTime createdAt;
}
