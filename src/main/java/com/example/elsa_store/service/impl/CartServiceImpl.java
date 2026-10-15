package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.AddToCartRequest;
import com.example.elsa_store.dto.request.UpdateCartItemRequest;
import com.example.elsa_store.dto.response.CartItemResponse;
import com.example.elsa_store.dto.response.CartResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Giỏ hàng của khách. Giá trong giỏ luôn là giá hiện tại (tính lại theo khuyến mãi mỗi lần xem), số lượng không vượt
 * tồn kho đang theo dõi. Giỏ chỉ chứa sản phẩm của một cửa hàng (mỗi đơn thuộc một cửa hàng).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    static final int MAX_QUANTITY = 999;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final PricingService pricingService;
    private final StockService stockService;

    @Override
    public ApiResponse<CartResponse> getMyCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Override
    public ApiResponse<CartResponse> addItem(Long userId, AddToCartRequest req) {
        int addQty = (req.getQuantity() == null || req.getQuantity() <= 0) ? 1 : req.getQuantity();
        Cart cart = getOrCreateCart(userId);

        ProductVariant variant = productVariantRepository.findById(req.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        if (!sellable(variant)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not available");
        }
        // Mỗi đơn thuộc một cửa hàng nên giỏ hàng chỉ chứa sản phẩm của một cửa hàng.
        Long variantStore = storeIdOf(variant);
        for (CartItem existing : cart.getItems()) {
            if (!Objects.equals(storeIdOf(existing.getProductVariant()), variantStore)) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Cart can only contain products from one store. Clear the cart to add this product");
            }
        }

        CartItem item = cartItemRepository
                .findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .orElse(null);
        int newQty = Math.min((item == null ? 0 : item.getQuantity()) + addQty, MAX_QUANTITY);
        stockService.requireAvailable(variant, newQty);

        if (item == null) {
            // Tính giá trước khi thêm vào giỏ: truy vấn khuyến mãi làm Hibernate tự flush, mà dòng mới chưa có giá thì
            // vi phạm cột NOT NULL.
            Long productId = variant.getProduct().getId();
            PricingService.Discounted price = PricingService.best(
                    CatalogPresenter.effectivePrice(variant),
                    pricingService.productPromotions(List.of(productId)).get(productId));
            item = CartItem.builder()
                    .cart(cart)
                    .productVariant(variant)
                    .quantity(newQty)
                    .unitPrice(price.salePrice())
                    .lineTotal(PricingService.money(price.salePrice() * newQty))
                    .build();
            cart.getItems().add(item);
        } else {
            item.setQuantity(newQty);
        }
        reprice(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Override
    public ApiResponse<CartResponse> updateItemQuantity(Long userId, Long cartItemId, UpdateCartItemRequest req) {
        if (req.getQuantity() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "quantity: must not be null");
        }
        Cart cart = getOrCreateCart(userId);
        CartItem item = ownItem(cart, cartItemId);

        if (req.getQuantity() <= 0) {
            cart.getItems().removeIf(i -> i.getId().equals(item.getId()));
            cartItemRepository.delete(item);
        } else {
            int quantity = Math.min(req.getQuantity(), MAX_QUANTITY);
            // Giảm số lượng luôn được (kể cả khi kho vừa hết); tăng thì không vượt tồn kho.
            if (quantity > item.getQuantity()) {
                stockService.requireAvailable(item.getProductVariant(), quantity);
            }
            item.setQuantity(quantity);
        }
        reprice(cart);
        return toResponse(cart);
    }

    @Override
    public ApiResponse<CartResponse> removeItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = ownItem(cart, cartItemId);
        cart.getItems().removeIf(i -> i.getId().equals(item.getId()));
        cartItemRepository.delete(item);
        reprice(cart);
        return toResponse(cart);
    }

    @Override
    public ApiResponse<CartResponse> clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        // orphanRemoval=true => chỉ cần clear list là đủ nếu JPA đang manage
        cart.getItems().clear();
        cart.setTotalAmount(0.0);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    private CartItem ownItem(Cart cart, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return item;
    }

    /** Giỏ hàng là của chính tài khoản đăng nhập (ADMIN không mua hàng). */
    private void requireOwner(Long userId) {
        User current = accessGuard.currentUser();
        accessGuard.rejectAdmin(current);
        if (!current.getId().equals(userId)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
    }

    private static boolean sellable(ProductVariant variant) {
        boolean variantActive = variant.getStatus() == null || variant.getStatus() == 1;
        return variantActive && ProductServiceImpl.isPublic(variant.getProduct());
    }

    private static Long storeIdOf(ProductVariant variant) {
        return variant.getProduct() != null && variant.getProduct().getStore() != null
                ? variant.getProduct().getStore().getId()
                : null;
    }

    private Cart getOrCreateCart(Long userId) {
        requireOwner(userId);
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return cartRepository.save(Cart.builder().user(user).totalAmount(0.0).build());
        });
    }

    /** Đơn giá theo giá và khuyến mãi hiện tại (giá trong giỏ không "đóng băng" từ lúc thêm). */
    private void reprice(Cart cart) {
        Map<Long, List<Promotion>> promos = pricingService.productPromotions(cart.getItems().stream()
                .map(i -> i.getProductVariant().getProduct().getId())
                .distinct()
                .toList());
        double total = 0;
        for (CartItem item : cart.getItems()) {
            ProductVariant v = item.getProductVariant();
            PricingService.Discounted price =
                    PricingService.best(CatalogPresenter.effectivePrice(v), promos.get(v.getProduct().getId()));
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            item.setUnitPrice(price.salePrice());
            item.setLineTotal(PricingService.money(price.salePrice() * quantity));
            total += item.getLineTotal();
        }
        cart.setTotalAmount(PricingService.money(total));
    }

    private ApiResponse<CartResponse> toResponse(Cart cart) {
        reprice(cart);
        List<CartItem> items = cart.getItems();
        Map<Long, List<Promotion>> promos = pricingService.productPromotions(items.stream()
                .map(i -> i.getProductVariant().getProduct().getId())
                .distinct()
                .toList());
        Map<Long, Integer> stock = stockService.levels(items.stream()
                .map(i -> i.getProductVariant().getId())
                .toList());
        Store store = items.isEmpty() ? null : items.getFirst().getProductVariant().getProduct().getStore();
        CartResponse cartResponse = CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUser().getId())
                .items(items.stream().map(i -> toItemResponse(i, promos, stock)).toList())
                .totalAmount(cart.getTotalAmount() == null ? 0.0 : cart.getTotalAmount())
                .storeId(store == null ? null : store.getId())
                .storeName(store == null ? null : store.getName())
                .build();
        return ApiResponse.ok(cartResponse);
    }

    private static CartItemResponse toItemResponse(
            CartItem item, Map<Long, List<Promotion>> promos, Map<Long, Integer> stock) {
        ProductVariant v = item.getProductVariant();
        Product product = v.getProduct();
        PricingService.Discounted price = PricingService.best(CatalogPresenter.effectivePrice(v), promos.get(product.getId()));
        return CartItemResponse.builder()
                .id(item.getId())
                .productVariantId(v.getId())
                .productId(product.getId())
                .productName(product.getName())
                .color(v.getColor())
                .size(v.getSize())
                .sku(v.getSku())
                .imageUrl(v.getImageUrl() != null ? v.getImageUrl() : product.getImageUrl())
                .unitPrice(item.getUnitPrice())
                .originalUnitPrice(price.originalPrice())
                .promotionName(price.discounted() ? price.promotionName() : null)
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .availableStock(stock.get(v.getId()))
                .unavailable(!sellable(v))
                .build();
    }
}
