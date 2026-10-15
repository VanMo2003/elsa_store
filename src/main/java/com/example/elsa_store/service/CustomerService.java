package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.CustomerRequest;
import com.example.elsa_store.dto.response.CustomerResponse;

import java.util.List;

public interface CustomerService {
    /** Chủ / quản lý / nhân viên tạo hồ sơ khách vãng lai tại quầy (không cần tài khoản). */
    CustomerResponse create(CustomerRequest req);

    CustomerResponse update(Long id, CustomerRequest req);

    /** Chỉ chủ và quản lý; không xoá được khách đã có đơn hàng. */
    void delete(Long id);

    CustomerResponse getById(Long id);

    /** Khách của cửa hàng người gọi (đã mua ở đó hoặc do cửa hàng tạo); ADMIN xem tất cả. */
    List<CustomerResponse> getAll();

    /** Tìm khách theo số điện thoại — nhân viên tra cứu trước khi tạo hồ sơ mới. */
    List<CustomerResponse> searchByPhone(String phone);

    /** Hồ sơ khách hàng của tài khoản USER đang đăng nhập (tạo nếu chưa có). */
    CustomerResponse getMe();

    CustomerResponse updateMe(CustomerRequest req);
}
