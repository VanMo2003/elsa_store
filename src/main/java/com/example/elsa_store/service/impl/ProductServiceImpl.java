
package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductImageResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductImage;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.ProductMapper;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductImageRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.ProductService;
import com.example.elsa_store.specification.ProductSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Duyệt sản phẩm là công khai (chỉ hàng đang bán của cửa hàng đã duyệt). Tạo và sửa: chủ, quản lý, nhân viên của
 * cửa hàng đó. Xoá: chỉ chủ và quản lý. ADMIN chỉ xem.
 */
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    /** Số ảnh tối đa trong bộ ảnh của một sản phẩm. */
    static final int MAX_IMAGES = 10;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AccessGuard accessGuard;
    private final CatalogPresenter catalog;
    private final WishlistItemRepository wishlistRepository;
    private OrderItemRepository orderItemRepository;

    /** Gợi ý "mua cùng" (tiêm qua setter để giữ nguyên hàm dựng dùng trong test). */
    @Autowired
    void setOrderItemRepository(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              FileStorageService fileStorageService,
                              ProductImageRepository productImageRepository,
                              ProductVariantRepository productVariantRepository,
                              AccessGuard accessGuard,
                              CatalogPresenter catalog,
                              WishlistItemRepository wishlistRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.productImageRepository = productImageRepository;
        this.productVariantRepository = productVariantRepository;
        this.accessGuard = accessGuard;
        this.catalog = catalog;
        this.wishlistRepository = wishlistRepository;
    }

    @Override
    public ProductResponse create(ProductRequest request, List<MultipartFile> files) {
        Store store = accessGuard.requireStoreMember();
        Category category = findCategory(request.getCategoryId());
        Product product = ProductMapper.toEntity(request, category);
        product.setStore(store);
        product = productRepository.save(product);

        if (files != null && !files.isEmpty()) {
            List<String> urls = files.stream()
                    .map(fileStorageService::storeProductImage)
                    .toList();

            Product findProduct = product;
            List<ProductImage> images = urls.stream()
                    .map(url -> ProductImage.builder()
                            .imageUrl(url)
                            .product(findProduct)
                            .build())
                    .toList();

            productImageRepository.saveAll(images);

            if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
                product.setImageUrl(urls.getFirst());
                productRepository.save(product);
            }
        }

        return ProductMapper.toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        ProductMapper.updateEntity(product, request, findCategory(request.getCategoryId()));
        return ProductMapper.toResponse(product);
    }

    @Override
    public void delete(Long id) {
        Product product = find(id);
        accessGuard.authorize(product.getStore(), Access.MANAGER_WRITE);
        // Khách lưu yêu thích không được chặn việc xoá sản phẩm (đơn hàng đã có thì vẫn chặn như trước).
        wishlistRepository.deleteAllByProductId(product.getId());
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getById(Long id) {
        Product product = find(id);
        if (!isPublic(product) && !canSeeHidden(product)) {
            throw new ResourceNotFoundException("Product not found");
        }
        List<String> productImages = productImageRepository.findAllByProduct_Id(id).stream()
                .map(ProductImage::getImageUrl)
                .toList();
        return catalog.detail(product, productVariantRepository.findAllByProduct_Id(id), productImages);
    }

    @Override
    public List<String> uploadImages(Long productId, List<MultipartFile> files) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id=" + productId));
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "files: must not be empty");
        }
        if (productImageRepository.countByProduct_Id(productId) + files.size() > MAX_IMAGES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "A product can have at most " + MAX_IMAGES + " images");
        }

        List<String> urls = files.stream().map(fileStorageService::storeProductImage).toList();

        List<ProductImage> images = urls.stream().map(url ->
                ProductImage.builder()
                        .imageUrl(url)
                        .product(product)
                        .build()
        ).toList();

        productImageRepository.saveAll(images);
        if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
            product.setImageUrl(urls.getFirst());
        }
        return urls;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> images(Long productId) {
        Product product = find(productId);
        accessGuard.authorize(product.getStore(), Access.READ);
        return imageList(product);
    }

    /**
     * Chỉ gỡ khỏi bộ ảnh, không xoá file: tin nhắn chat đã gửi kèm thẻ sản phẩm vẫn giữ đường dẫn ảnh cũ.
     */
    @Override
    public List<ProductImageResponse> deleteImage(Long productId, Long imageId) {
        Product product = find(productId);
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        ProductImage image = findImage(productId, imageId);
        productImageRepository.delete(image);
        productImageRepository.flush();
        if (image.getImageUrl().equals(product.getImageUrl())) {
            product.setImageUrl(productImageRepository.findAllByProduct_IdOrderByIdAsc(productId).stream()
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(null));
        }
        return imageList(product);
    }

    @Override
    public List<ProductImageResponse> setCover(Long productId, Long imageId) {
        Product product = find(productId);
        accessGuard.authorize(product.getStore(), Access.STAFF_WRITE);
        product.setImageUrl(findImage(productId, imageId).getImageUrl());
        return imageList(product);
    }

    private ProductImage findImage(Long productId, Long imageId) {
        return productImageRepository.findByIdAndProduct_Id(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
    }

    private List<ProductImageResponse> imageList(Product product) {
        return productImageRepository.findAllByProduct_IdOrderByIdAsc(product.getId()).stream()
                .map(image -> ProductImageResponse.builder()
                        .id(image.getId())
                        .url(image.getImageUrl())
                        .cover(image.getImageUrl().equals(product.getImageUrl()))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return catalog.products(productRepository.findPublic(null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllByCategory(Long categoryId) {
        return catalog.products(productRepository.findPublic(categoryId, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllByStore(Long storeId) {
        return catalog.products(productRepository.findPublic(null, storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> related(Long productId, int size) {
        Product product = find(productId);
        if (!isPublic(product)) {
            throw new ResourceNotFoundException("Product not found");
        }
        int limit = Math.min(Math.max(size, 1), 20);
        // Lấy dư một dòng vì chính sản phẩm này có thể nằm trong kết quả.
        Pageable firstPage = PageRequest.of(0, limit + 1);
        Map<Long, Product> picked = new LinkedHashMap<>();
        if (product.getCategory() != null) {
            productRepository.findPublic(product.getCategory().getId(), null, firstPage)
                    .forEach(p -> picked.putIfAbsent(p.getId(), p));
        }
        picked.remove(product.getId());
        if (picked.size() < limit && product.getStore() != null) {
            productRepository.findPublic(null, product.getStore().getId(), PageRequest.of(0, limit + 1))
                    .forEach(p -> picked.putIfAbsent(p.getId(), p));
            picked.remove(product.getId());
        }
        return catalog.products(picked.values().stream().limit(limit).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> boughtTogether(Long productId, int size) {
        Product product = find(productId);
        if (!isPublic(product)) {
            throw new ResourceNotFoundException("Product not found");
        }
        int limit = Math.min(Math.max(size, 1), 20);
        if (orderItemRepository == null) {
            return List.of();
        }
        // Lấy dư vì sản phẩm đã ẩn / cửa hàng ngừng bán bị bỏ khỏi gợi ý.
        List<Long> ids = orderItemRepository.boughtTogether(productId, PageRequest.of(0, limit * 2)).stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        Map<Long, Product> byId = new LinkedHashMap<>();
        productRepository.findAllById(ids).forEach(p -> byId.put(p.getId(), p));
        return catalog.products(ids.stream()
                .map(byId::get)
                .filter(p -> p != null && isPublic(p))
                .limit(limit)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getMine() {
        Store store = accessGuard.requireStoreMember();
        return catalog.products(productRepository.findAllByStore_Id(store.getId()));
    }

    static final int MAX_SEARCH_PAGE_SIZE = 50;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSearchRequest request) {
        int page = request.getPage() == null ? 0 : Math.max(0, request.getPage());
        int size = request.getSizePage() == null ? 20 : Math.min(Math.max(1, request.getSizePage()), MAX_SEARCH_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size, searchSort(request.getSortBy(), request.getSortDir()));
        Page<Product> result = productRepository.findAll(ProductSpecification.search(request), pageable);
        return PageResponse.<ProductResponse>builder()
                .content(catalog.products(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * Chỉ sắp xếp theo các trường cho phép — tên trường lạ từ người gọi trước đây làm lỗi 500. Mặc định: mới nhất trước.
     */
    static Sort searchSort(String sortBy, String sortDir) {
        String key = sortBy == null ? "" : sortBy.trim().toLowerCase(java.util.Locale.ROOT);
        boolean desc = sortDir != null && sortDir.equalsIgnoreCase("desc");
        Sort.Direction direction = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        return switch (key) {
            case "price", "baseprice" -> Sort.by(direction, "basePrice").and(Sort.by(Sort.Direction.DESC, "id"));
            case "name" -> Sort.by(direction, "name").and(Sort.by(Sort.Direction.DESC, "id"));
            case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    /** Đang bán và (nếu có) cửa hàng đã được duyệt. */
    static boolean isPublic(Product product) {
        boolean active = product.getStatus() == null || product.getStatus() == 1;
        boolean storeOpen = product.getStore() == null || product.getStore().isOpenToCustomers();
        return active && storeOpen;
    }

    /** ADMIN, hoặc người của chính cửa hàng đó, xem được cả sản phẩm đang ẩn. */
    private boolean canSeeHidden(Product product) {
        User current = accessGuard.currentUserOrEmpty().orElse(null);
        if (current == null) {
            return false;
        }
        if (current.getRole() == Role.ADMIN) {
            return true;
        }
        Store mine = accessGuard.storeOf(current);
        return mine != null && product.getStore() != null && mine.getId().equals(product.getStore().getId());
    }
}
