
package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.SupplierRequest;
import com.example.elsa_store.dto.response.SupplierResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.Supplier;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.SupplierMapper;
import com.example.elsa_store.repository.SupplierRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.SupplierService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Nhà cung cấp của từng cửa hàng: chỉ chủ và quản lý thao tác; ADMIN chỉ xem. */
@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final AccessGuard accessGuard;

    public SupplierServiceImpl(SupplierRepository supplierRepository, AccessGuard accessGuard) {
        this.supplierRepository = supplierRepository;
        this.accessGuard = accessGuard;
    }

    @Override
    public SupplierResponse create(SupplierRequest req) {
        Store store = accessGuard.requireStoreManager();
        Supplier s = SupplierMapper.toEntity(req);
        s.setStore(store);
        s = supplierRepository.save(s);
        return SupplierMapper.toResponse(s);
    }

    @Override
    public SupplierResponse update(Long id, SupplierRequest req) {
        Supplier s = find(id);
        accessGuard.authorize(s.getStore(), Access.MANAGER_WRITE);
        SupplierMapper.update(s, req);
        return SupplierMapper.toResponse(s);
    }

    @Override
    public void delete(Long id) {
        Supplier s = find(id);
        accessGuard.authorize(s.getStore(), Access.MANAGER_WRITE);
        supplierRepository.delete(s);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        Supplier s = find(id);
        accessGuard.authorize(s.getStore(), Access.MANAGER_READ);
        return SupplierMapper.toResponse(s);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getAll() {
        var current = accessGuard.currentUser();
        List<Supplier> data = current.getRole() == Role.ADMIN
                ? supplierRepository.findAll()
                : supplierRepository.findAllByStore_Id(accessGuard.requireStoreManager().getId());
        return data.stream().map(SupplierMapper::toResponse).toList();
    }

    private Supplier find(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
    }
}
