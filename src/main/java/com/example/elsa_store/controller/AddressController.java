
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AddressRequest;
import com.example.elsa_store.dto.response.AddressResponse;
import com.example.elsa_store.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public ApiResponse<AddressResponse> create(@Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(addressService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(addressService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        addressService.delete(id);
        return ApiResponse.ok(null);
    }

    /** Sổ địa chỉ của khách đang đăng nhập; địa chỉ mặc định lên đầu. */
    @GetMapping("/me")
    public ApiResponse<List<AddressResponse>> getMine() {
        return ApiResponse.ok(addressService.getMine());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<AddressResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(addressService.getById(id));
    }

    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<AddressResponse>> getByCustomer(@PathVariable Long customerId) {
        return ApiResponse.ok(addressService.getAllByCustomer(customerId));
    }
}
