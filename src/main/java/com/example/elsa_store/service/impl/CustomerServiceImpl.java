
package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.CustomerRequest;
import com.example.elsa_store.dto.response.CustomerResponse;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.CustomerMapper;
import com.example.elsa_store.repository.CustomerRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Khách hàng. Tài khoản USER có sẵn một hồ sơ; nhân viên tạo thêm hồ sơ khách vãng lai (không có tài khoản) khi lập
 * đơn tại quầy. Nhân viên chỉ thấy khách của cửa hàng mình (đã mua ở đó hoặc do cửa hàng tạo).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final UserAccountFactory accountFactory;
    private final AccessGuard accessGuard;

    @Override
    public CustomerResponse create(CustomerRequest req) {
        Store store = accessGuard.requireStoreMember();
        if (req.getFullName() == null || req.getFullName().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "fullName: must not be blank");
        }
        if (req.getPhone() == null || req.getPhone().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "phone: must not be blank");
        }
        if (!customerRepository.findAllByPhone(req.getPhone().trim()).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "A customer with this phone already exists");
        }
        // Khách vãng lai không có tài khoản: bỏ qua userId của request.
        Customer c = CustomerMapper.toEntity(req, null);
        c.setStore(store);
        return CustomerMapper.toResponse(customerRepository.save(c));
    }

    @Override
    public CustomerResponse update(Long id, CustomerRequest req) {
        Customer c = find(id);
        authorize(c, true);
        CustomerMapper.update(c, req);
        return CustomerMapper.toResponse(customerRepository.save(c));
    }

    @Override
    public void delete(Long id) {
        Customer c = find(id);
        accessGuard.requireStoreManager();
        authorize(c, true);
        if (c.getUser() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Customers with an account cannot be deleted");
        }
        if (orderRepository.existsByCustomer_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Customers with orders cannot be deleted");
        }
        customerRepository.delete(c);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer c = find(id);
        authorize(c, false);
        return CustomerMapper.toResponse(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        User current = accessGuard.currentUser();
        List<Customer> data = current.getRole() == Role.ADMIN
                ? customerRepository.findAll()
                : customerRepository.findAllOfStore(accessGuard.requireStoreMember().getId());
        return data.stream().map(CustomerMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> searchByPhone(String phone) {
        Store store = accessGuard.requireStoreMember();
        // Chỉ tra cứu được hồ sơ khách vãng lai của cửa hàng mình và khách có tài khoản (để gắn vào đơn tại quầy).
        return customerRepository.findAllByPhone(phone == null ? "" : phone.trim()).stream()
                .filter(c -> c.getUser() != null
                        || (c.getStore() != null && c.getStore().getId().equals(store.getId())))
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @Override
    public CustomerResponse getMe() {
        return CustomerMapper.toResponse(accountFactory.ensureCustomerProfile(accessGuard.requireRole(Role.USER)));
    }

    @Override
    public CustomerResponse updateMe(CustomerRequest req) {
        Customer c = accountFactory.ensureCustomerProfile(accessGuard.requireRole(Role.USER));
        c.setGender(req.getGender());
        c.setDob(req.getDob());
        return CustomerMapper.toResponse(customerRepository.save(c));
    }

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    /** Chính chủ, hoặc người của cửa hàng có khách này, hoặc ADMIN (chỉ xem). */
    private void authorize(Customer c, boolean write) {
        User current = accessGuard.currentUser();
        switch (current.getRole()) {
            case ADMIN -> {
                if (write) {
                    throw new AppException(ErrorCode.ADMIN_READ_ONLY);
                }
            }
            case USER -> {
                if (c.getUser() == null || !c.getUser().getId().equals(current.getId())) {
                    throw new AppException(ErrorCode.UN_AUTHORIZED);
                }
            }
            default -> {
                Store mine = accessGuard.requireStoreMember();
                boolean inStore = (c.getStore() != null && c.getStore().getId().equals(mine.getId()))
                        || orderRepository.existsByCustomer_IdAndStore_Id(c.getId(), mine.getId());
                if (!inStore) {
                    throw new AppException(ErrorCode.UN_AUTHORIZED);
                }
            }
        }
    }
}
