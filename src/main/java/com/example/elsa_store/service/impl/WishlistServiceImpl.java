package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.WishlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WishlistServiceImpl implements WishlistService {

    /** Đủ dùng cho một khách, chặn dùng API để nhét bảng vô hạn. */
    static final int MAX_ITEMS = 200;

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final AccessGuard accessGuard;
    private final CatalogPresenter catalog;

    public WishlistServiceImpl(WishlistItemRepository wishlistRepository,
                               ProductRepository productRepository,
                               AccessGuard accessGuard,
                               CatalogPresenter catalog) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.accessGuard = accessGuard;
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        User user = customer();
        List<Product> products = wishlistRepository.findByUser_IdOrderByIdDesc(user.getId()).stream()
                .map(WishlistItem::getProduct)
                .filter(ProductServiceImpl::isPublic)
                .toList();
        return catalog.products(products);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> productIds() {
        return wishlistRepository.productIds(customer().getId());
    }

    @Override
    public List<Long> add(Long productId) {
        User user = customer();
        if (wishlistRepository.findByUser_IdAndProduct_Id(user.getId(), productId).isEmpty()) {
            Product product = productRepository.findById(productId)
                    .filter(ProductServiceImpl::isPublic)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (wishlistRepository.countByUser_Id(user.getId()) >= MAX_ITEMS) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Wishlist is full");
            }
            WishlistItem item = new WishlistItem();
            item.setUser(user);
            item.setProduct(product);
            wishlistRepository.save(item);
        }
        return wishlistRepository.productIds(user.getId());
    }

    @Override
    public List<Long> remove(Long productId) {
        User user = customer();
        wishlistRepository.findByUser_IdAndProduct_Id(user.getId(), productId).ifPresent(wishlistRepository::delete);
        return wishlistRepository.productIds(user.getId());
    }

    private User customer() {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only customer accounts have a wishlist");
        }
        return user;
    }
}
