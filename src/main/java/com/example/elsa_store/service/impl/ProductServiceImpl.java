
package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.ProductVariantResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductImage;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.ProductMapper;
import com.example.elsa_store.mapper.ProductVariantMapper;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductImageRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.ProductService;
import com.example.elsa_store.specification.ProductSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository, FileStorageService fileStorageService, ProductImageRepository productImageRepository, ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.productImageRepository = productImageRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }
        Product product = ProductMapper.toEntity(request, category);
        product = productRepository.save(product);
        return ProductMapper.toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }
        ProductMapper.updateEntity(product, request, category);
        return ProductMapper.toResponse(product);
    }

    @Override
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found");
        }
        productRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getById(Long id) {
        List<ProductVariantResponse> productVariants = productVariantRepository.findAllByProduct_Id(id).stream().map(
                ProductVariantMapper::toResponse
        ).toList();
        List<String> productImages = productImageRepository.findAllByProduct_Id(id).stream().map(ProductImage::getImageUrl).toList();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductDetailResponse productDetailResponse = ProductMapper.toDetailResponse(product);
        productDetailResponse.setProductVariants(productVariants);
        productDetailResponse.setImages(productImages);

        return productDetailResponse;
    }

    @Override
    public List<String> uploadImages(Long productId, List<MultipartFile> files) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id=" + productId));

        List<String> urls = files.stream().map(fileStorageService::uploadHotelImage).toList();

        List<ProductImage> images = urls.stream().map(url ->
                ProductImage.builder()
                        .imageUrl(url)
                        .product(product)
                        .build()
        ).toList();

        productImageRepository.saveAll(images);

        return urls;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> getAllByCategory(Long categoryId) {
        return productRepository.findAllByCategory_Id(categoryId).stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(ProductSearchRequest request) {

        Sort sort = Sort.unsorted();

        if (request.getSortBy() != null) {
            sort = Sort.by(
                    request.getSortDir() != null && request.getSortDir().equalsIgnoreCase("desc")
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC,
                    request.getSortBy()
            );
        }

        Pageable pageable = PageRequest.of(request.getPage(), request.getSizePage(), sort);

        Page<Product> page = productRepository.findAll(
                ProductSpecification.search(request),
                pageable
        );

        return page.map(ProductMapper::toResponse);
    }
}
