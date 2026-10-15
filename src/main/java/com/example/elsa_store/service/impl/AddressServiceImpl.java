package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.AddressRequest;
import com.example.elsa_store.dto.response.AddressResponse;
import com.example.elsa_store.entity.Address;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.AddressMapper;
import com.example.elsa_store.repository.AddressRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Sổ địa chỉ giao hàng của khách: chỉ chính tài khoản USER đó được xem và sửa. */
@Service
@Transactional
public class AddressServiceImpl implements AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final UserAccountFactory accountFactory;
    private final AccessGuard accessGuard;

    public AddressServiceImpl(
            AddressRepository addressRepository,
            OrderRepository orderRepository,
            UserAccountFactory accountFactory,
            AccessGuard accessGuard) {
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.accountFactory = accountFactory;
        this.accessGuard = accessGuard;
    }

    /** "Số nhà, phường, quận, tỉnh" — chụp vào đơn lúc đặt. */
    public static String fullAddress(Address a) {
        return Stream.of(a.getDetail(), a.getWard(), a.getDistrict(), a.getProvince())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }

    private Customer ownCustomer() {
        return accountFactory.ensureCustomerProfile(accessGuard.requireRole(Role.USER));
    }

    private Customer requireOwn(Long customerId) {
        Customer own = ownCustomer();
        if (customerId != null && !own.getId().equals(customerId)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return own;
    }

    private Address findOwn(Long id) {
        Address a = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        requireOwn(a.getCustomer().getId());
        return a;
    }

    @Override
    public AddressResponse create(AddressRequest req) {
        Customer customer = requireOwn(req.getCustomerId());
        long existing = addressRepository.countByCustomer_Id(customer.getId());
        if (existing >= MAX_ADDRESSES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Too many saved addresses");
        }
        Address a = AddressMapper.toEntity(req, customer);
        // Địa chỉ đầu tiên luôn là mặc định.
        a.setIsDefault(existing == 0 || Boolean.TRUE.equals(req.getIsDefault()));
        a = addressRepository.save(a);
        if (Boolean.TRUE.equals(a.getIsDefault())) {
            addressRepository.clearDefaultExcept(customer.getId(), a.getId());
        }
        return AddressMapper.toResponse(a);
    }

    @Override
    public AddressResponse update(Long id, AddressRequest req) {
        Address a = findOwn(id);
        Customer customer = requireOwn(req.getCustomerId());
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        AddressMapper.update(a, req, customer);
        // Không bỏ được mặc định của địa chỉ đang mặc định bằng cách gửi false — chọn địa chỉ khác làm mặc định.
        a.setIsDefault(wasDefault || Boolean.TRUE.equals(req.getIsDefault()));
        a = addressRepository.save(a);
        if (Boolean.TRUE.equals(a.getIsDefault())) {
            addressRepository.clearDefaultExcept(customer.getId(), a.getId());
        }
        return AddressMapper.toResponse(a);
    }

    @Override
    public void delete(Long id) {
        Address a = findOwn(id);
        Long customerId = a.getCustomer().getId();
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        // Đơn cũ đã chụp lại người nhận / địa chỉ nên chỉ cần bỏ liên kết, không chặn xoá.
        orderRepository.detachAddress(id);
        addressRepository.delete(a);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findAllOfCustomer(customerId).stream().findFirst().ifPresent(next -> {
                next.setIsDefault(true);
                addressRepository.save(next);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getById(Long id) {
        return AddressMapper.toResponse(findOwn(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAllByCustomer(Long customerId) {
        requireOwn(customerId);
        return addressRepository.findAllOfCustomer(customerId).stream()
                .map(AddressMapper::toResponse)
                .toList();
    }

    @Override
    public List<AddressResponse> getMine() {
        Customer customer = ownCustomer();
        return addressRepository.findAllOfCustomer(customer.getId()).stream()
                .map(AddressMapper::toResponse)
                .toList();
    }
}
