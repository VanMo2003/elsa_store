
package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.request.CustomerRequest;
import com.example.elsa_store.dto.response.CustomerResponse;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.User;

public class CustomerMapper {

    /** Khách vãng lai do cửa hàng tạo tại quầy (không có tài khoản) hoặc hồ sơ gắn với một tài khoản. */
    public static Customer toEntity(CustomerRequest req, User user) {
        Customer c = new Customer();
        c.setUser(user);
        c.setFullName(blankToNull(req.getFullName()));
        c.setPhone(blankToNull(req.getPhone()));
        c.setEmail(blankToNull(req.getEmail()));
        c.setGender(req.getGender());
        c.setDob(req.getDob());
        return c;
    }

    public static void update(Customer c, CustomerRequest req) {
        if (req.getFullName() != null) {
            c.setFullName(blankToNull(req.getFullName()));
        }
        if (req.getPhone() != null) {
            c.setPhone(blankToNull(req.getPhone()));
        }
        if (req.getEmail() != null) {
            c.setEmail(blankToNull(req.getEmail()));
        }
        c.setGender(req.getGender());
        c.setDob(req.getDob());
    }

    public static CustomerResponse toResponse(Customer c) {
        CustomerResponse res = new CustomerResponse();
        User u = c.getUser();
        res.setId(c.getId());
        res.setUserId(u != null ? u.getId() : null);
        res.setGender(c.getGender());
        res.setDob(c.getDob());
        // Hồ sơ riêng của khách vãng lai ưu tiên; khách có tài khoản lấy thông tin từ tài khoản.
        res.setFullName(c.getFullName() != null ? c.getFullName() : u != null ? u.getFullName() : null);
        res.setEmail(c.getEmail() != null ? c.getEmail() : u != null ? u.getEmail() : null);
        res.setPhone(c.getPhone() != null ? c.getPhone() : u != null ? u.getPhone() : null);
        return res;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
