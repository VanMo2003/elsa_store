
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

    public ProductVariantServiceImpl(ProductVariantRepository variantRepository,
                                     ProductRepository productRepository, ProductVariantRepository productVariantRepository, FileStorageService fileStorageService, ProductVariantImageRepository productVariantImageRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.fileStorageService = fileStorageService;
        this.productVariantImageRepository = productVariantImageRepository;
    }

    @Override
    public ProductVariantResponse create(ProductVariantRequest req, List<MultipartFile> files) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductVariant productVariant = ProductVariantMapper.toEntity(req, product);
        productVariant = variantRepository.save(productVariant);

        if (files != null && !files.isEmpty()) {
            List<String> urls = files.stream()
                    .map(fileStorageService::uploadHotelImage)
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
        ProductVariantMapper.update(v, req, product);
        return ProductVariantMapper.toResponse(v);
    }

    @Override
    public void delete(Long id) {
        if (!variantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product variant not found");
        }
        variantRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantDetailResponse getById(Long id) {
        List<String> productVariantImages = productVariantImageRepository.findAllByProductVariant_Id(id).stream().map(ProductVariantImage::getImageUrl).toList();

        ProductVariant productVariant = variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        ProductVariantDetailResponse res = ProductVariantMapper.toDetailResponse(productVariant);
        res.setImages(productVariantImages);

        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAll(Long productId) {
        return variantRepository.findAllByProduct_Id(productId).stream()
                .map(ProductVariantMapper::toResponse)
                .toList();
    }

    @Override
    public List<String> uploadImages(Long productVariantId, List<MultipartFile> files) {
        ProductVariant productVariant = productVariantRepository.findById(productVariantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product VariantId not found with id=" + productVariantId));

        List<String> urls = files.stream().map(fileStorageService::uploadHotelImage).toList();

        List<ProductVariantImage> images = urls.stream().map(url ->
                ProductVariantImage.builder()
                        .imageUrl(url)
                        .productVariant(productVariant)
                        .build()
        ).toList();

        productVariantImageRepository.saveAll(images);

        return urls;
    }
}
