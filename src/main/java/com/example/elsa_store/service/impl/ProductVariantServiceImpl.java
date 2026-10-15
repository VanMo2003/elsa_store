
package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.ProductVariantRequest;
import com.example.elsa_store.dto.response.ProductVariantDetailResponse;
import com.example.elsa_store.dto.response.ProductVariantResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductImage;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.ProductVariantImage;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.ProductVariantMapper;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantImageRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.ProductVariantService;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final FileStorageService fileStorageService;
    private final ProductVariantImageRepository productVariantImageRepository;
    private final AccessGuard accessGuard;
    private final CatalogPresenter catalog;

    public ProductVariantServiceImpl(ProductVariantRepository variantRepository,
                                     ProductRepository productRepository, ProductVariantRepository productVariantRepository, FileStorageService fileStorageService, ProductVariantImageRepository productVariantImageRepository,
                                     AccessGuard accessGuard,
                                     CatalogPresenter catalog) {
        this.accessGuard = accessGuard;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.fileStorageService = fileStorageService;
        this.productVariantImageRepository = productVariantImageRepository;
        this.catalog = catalog;
    }

    @Override
    public ProductVariantResponse create(ProductVariantRequest req, List<MultipartFile> files) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        ProductVariant productVariant = ProductVariantMapper.toEntity(req, product);
        productVariant = variantRepository.save(productVariant);

        if (files != null && !files.isEmpty()) {
            List<String> urls = files.stream()
                    .map(fileStorageService::storeProductImage)
                    .toList();

            ProductVariant finalProductVariant = productVariant;
            List<ProductVariantImage> images = urls.stream()
                    .map(url -> ProductVariantImage.builder()
                            .imageUrl(url)
                            .productVariant(finalProductVariant)
                            .build())
                    .toList();

            productVariantImageRepository.saveAll(images);

            if (productVariant.getImageUrl() == null || productVariant.getImageUrl().isBlank()) {
                productVariant.setImageUrl(urls.getFirst());
                productVariantRepository.save(productVariant);
            }
        }

        return ProductVariantMapper.toResponse(productVariant);
    }

    @Override
    public ProductVariantResponse update(Long id, ProductVariantRequest req) {
        ProductVariant v = variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        // Vừa phải sửa được biến thể hiện tại, vừa phải sửa được sản phẩm đích (cùng một cửa hàng).
        accessGuard.authorize(v.getProduct().getStore(), Access.STAFF_WRITE);
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        ProductVariantMapper.update(v, req, product);
        return ProductVariantMapper.toResponse(v);
    }

    @Override
    public void delete(Long id) {
        ProductVariant variant = variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        accessGuard.authorize(variant.getProduct().getStore(), Access.MANAGER_WRITE);
        variantRepository.delete(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantDetailResponse getById(Long id) {
        List<String> productVariantImages = productVariantImageRepository.findAllByProductVariant_Id(id).stream().map(ProductVariantImage::getImageUrl).toList();

        ProductVariant productVariant = variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        requireVisible(productVariant.getProduct());

        return catalog.variantDetail(productVariant, productVariantImages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAll(Long productId) {
        requireVisible(productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found")));
        return catalog.variants(variantRepository.findAllByProduct_Id(productId));
    }

    @Override
    public List<String> uploadImages(Long productVariantId, List<MultipartFile> files) {
        ProductVariant productVariant = productVariantRepository.findById(productVariantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product VariantId not found with id=" + productVariantId));
        accessGuard.authorize(productVariant.getProduct().getStore(), Access.STAFF_WRITE);

        List<String> urls = files.stream().map(fileStorageService::storeProductImage).toList();

        List<ProductVariantImage> images = urls.stream().map(url ->
                ProductVariantImage.builder()
                        .imageUrl(url)
                        .productVariant(productVariant)
                        .build()
        ).toList();

        productVariantImageRepository.saveAll(images);

        return urls;
    }

    /** Sản phẩm phải đang bán và thuộc cửa hàng đã duyệt; ADMIN và người của cửa hàng đó xem được cả hàng ẩn. */
    private void requireVisible(Product product) {
        if (ProductServiceImpl.isPublic(product)) {
            return;
        }
        var current = accessGuard.currentUserOrEmpty().orElse(null);
        boolean allowed = current != null
                && (current.getRole() == com.example.elsa_store.constant.Role.ADMIN
                        || (accessGuard.storeOf(current) != null
                                && product.getStore() != null
                                && accessGuard.storeOf(current).getId().equals(product.getStore().getId())));
        if (!allowed) {
            throw new ResourceNotFoundException("Product not found");
        }
    }
}
