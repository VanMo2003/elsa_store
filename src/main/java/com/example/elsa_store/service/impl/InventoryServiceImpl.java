
package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.InventoryRequest;
import com.example.elsa_store.dto.response.InventoryResponse;
import com.example.elsa_store.entity.Inventory;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.InventoryMapper;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                ProductVariantRepository productVariantRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    public InventoryResponse create(InventoryRequest req) {
        if (inventoryRepository.findByProductVariant_Id(req.getProductVariantId()).isPresent()) {
            throw new RuntimeException("Inventory already exists for this variant");
        }
        ProductVariant variant = productVariantRepository.findById(req.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        Inventory i = InventoryMapper.toEntity(req, variant);
        i = inventoryRepository.save(i);
        return InventoryMapper.toResponse(i);
    }

    @Override
    public InventoryResponse update(Long id, InventoryRequest req) {
        Inventory i = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        ProductVariant variant = productVariantRepository.findById(req.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        InventoryMapper.update(i, req, variant);
        return InventoryMapper.toResponse(i);
    }

    @Override
    public void delete(Long id) {
        if (!inventoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Inventory not found");
        }
        inventoryRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getById(Long id) {
        Inventory i = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        return InventoryMapper.toResponse(i);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAll() {
        return inventoryRepository.findAll().stream()
                .map(InventoryMapper::toResponse)
                .toList();
    }
}
