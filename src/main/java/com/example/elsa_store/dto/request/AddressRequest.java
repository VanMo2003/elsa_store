
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Địa chỉ giao hàng trong sổ địa chỉ của khách. {@code customerId} có thể bỏ trống: mặc định là chính khách đó. */
public class AddressRequest {

    private Long customerId;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "[0-9+ .()-]{8,20}", message = "invalid")
    private String phone;

    @Size(max = 100)
    private String province;

    @Size(max = 100)
    private String district;

    @Size(max = 100)
    private String ward;

    @NotBlank
    @Size(max = 255)
    private String detail;

    private Boolean isDefault;

    public Long getCustomerId() { return customerId; }

    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getProvince() { return province; }

    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }

    public void setDistrict(String district) { this.district = district; }

    public String getWard() { return ward; }

    public void setWard(String ward) { this.ward = ward; }

    public String getDetail() { return detail; }

    public void setDetail(String detail) { this.detail = detail; }

    public Boolean getIsDefault() { return isDefault; }

    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}
