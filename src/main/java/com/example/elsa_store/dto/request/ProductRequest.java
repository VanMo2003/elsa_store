
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Min(0)
    private Double basePrice;

    private Long categoryId;
    /** 1: đang bán, 0: ẩn; bỏ trống thì giữ nguyên. */
    private Integer status;

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public Double getBasePrice() { return basePrice; }

    public void setBasePrice(Double basePrice) { this.basePrice = basePrice; }

    public Integer getStatus() { return status; }

    public void setStatus(Integer status) { this.status = status; }

    public Long getCategoryId() { return categoryId; }

    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
