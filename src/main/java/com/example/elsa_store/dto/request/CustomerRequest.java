
package com.example.elsa_store.dto.request;

import java.time.LocalDate;

public class CustomerRequest {

    /** Tài khoản của khách; bỏ trống với khách vãng lai do nhân viên tạo tại quầy. */
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private Integer gender;
    private LocalDate dob;

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getGender() { return gender; }

    public void setGender(Integer gender) { this.gender = gender; }

    public LocalDate getDob() { return dob; }

    public void setDob(LocalDate dob) { this.dob = dob; }
}
