
package com.example.elsa_store.dto.request;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PromotionRequest {

    private String name;
    private String description;
    private Integer type;
    private Double value;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String couponCode;
    private Double minOrderValue;

    /**
     * Sản phẩm được áp (cùng cửa hàng). {@code null} = giữ nguyên danh sách đang có; rỗng = không gắn sản phẩm nào (khuyến
     * mãi không mã sẽ áp cho cả đơn). Lưu cùng giao dịch với khuyến mãi nên không thể "lưu nửa chừng".
     */
    private List<Long> productIds;

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public Integer getType() { return type; }

    public void setType(Integer type) { this.type = type; }

    public Double getValue() { return value; }

    public void setValue(Double value) { this.value = value; }

    public LocalDate getStartDate() { return startDate; }

    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }

    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getStatus() { return status; }

    public void setStatus(Integer status) { this.status = status; }

    public String getCouponCode() { return couponCode; }

    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public Double getMinOrderValue() { return minOrderValue; }

    public void setMinOrderValue(Double minOrderValue) { this.minOrderValue = minOrderValue; }

    public List<Long> getProductIds() { return productIds; }

    public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
}
