
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.CustomerRequest;
import com.example.elsa_store.dto.response.CustomerResponse;
import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import com.example.elsa_store.service.CustomerSegments;
import com.example.elsa_store.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerSegments customerSegments;

    public CustomerController(CustomerService customerService, CustomerSegments customerSegments) {
        this.customerService = customerService;
        this.customerSegments = customerSegments;
    }

    /**
     * Chủ / quản lý: phân nhóm khách (VIP, thân thiết, mới, sắp rời bỏ, đã lâu không mua) theo đơn đã hoàn thành ở cửa
     * hàng; {@code segment} trống = mọi nhóm. Trả số khách mỗi nhóm và danh sách, chi nhiều nhất trước.
     */
    @GetMapping("/segments")
    public ApiResponse<CustomerSegmentsResponse> segments(@RequestParam(required = false) CustomerSegment segment) {
        return ApiResponse.ok(customerSegments.list(segment));
    }

    /** Nhân viên tạo hồ sơ khách vãng lai tại quầy. */
    @PostMapping
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest req) {
        return ApiResponse.ok(customerService.create(req));
    }

    /** Hồ sơ khách hàng của tài khoản USER đang đăng nhập. */
    @GetMapping("/me")
    public ApiResponse<CustomerResponse> getMe() {
        return ApiResponse.ok(customerService.getMe());
    }

    @PutMapping("/me")
    public ApiResponse<CustomerResponse> updateMe(@Valid @RequestBody CustomerRequest req) {
        return ApiResponse.ok(customerService.updateMe(req));
    }

    /** Tra cứu khách theo số điện thoại (nhân viên). */
    @GetMapping("/search")
    public ApiResponse<List<CustomerResponse>> search(@RequestParam String phone) {
        return ApiResponse.ok(customerService.searchByPhone(phone));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CustomerRequest req) {
        return ApiResponse.ok(customerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(customerService.getById(id));
    }

    /** Khách của cửa hàng người gọi. */
    @GetMapping
    public ApiResponse<List<CustomerResponse>> getAll() {
        return ApiResponse.ok(customerService.getAll());
    }
}
