package com.example.elsa_store.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PromotionResponse {

    private Long id;
    private String name;
    private String description;
    /** 1 = giảm số tiền, 2 = giảm phần trăm. */
    private Integer type;
    private Double value;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 1 = đang chạy, 0 = tạm dừng. */
    private Integer status;
    private String couponCode;
    private Double minOrderValue;
    /** Sản phẩm được áp; rỗng = áp cho cả đơn. */
    private List<Long> productIds;
    /** Đang có hiệu lực hôm nay (đang chạy và trong khoảng ngày). */
    private Boolean activeNow;
}
