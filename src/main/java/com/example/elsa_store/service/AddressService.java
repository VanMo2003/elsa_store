
package com.example.elsa_store.service;


import com.example.elsa_store.dto.request.AddressRequest;
import com.example.elsa_store.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {
    AddressResponse create(AddressRequest req);
    AddressResponse update(Long id, AddressRequest req);
    void delete(Long id);
    AddressResponse getById(Long id);
    List<AddressResponse> getAllByCustomer(Long customerId);

    /** Sổ địa chỉ của khách đang đăng nhập (mặc định lên đầu). */
    List<AddressResponse> getMine();
}
