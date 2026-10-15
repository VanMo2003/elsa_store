package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * "Có hàng trở lại": khi kho được cộng (nhập hàng, kiểm kê, hoàn kho do huỷ đơn / trả hàng) làm một sản phẩm đang
 * <b>hết hàng</b> có hàng lại, báo cho khách đã thả tim sản phẩm đó. Chỉ báo lúc chuyển từ hết sang còn, nên nhập nhiều
 * phân loại cùng lúc vẫn chỉ báo một lần. Thông báo lưu cùng giao dịch (giao dịch hỏng thì không có thông báo); push gửi
 * sau khi commit.
 */
@Component
public class BackInStock {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WishlistItemRepository wishlistRepository;
    private final NotificationService notificationService;

    public BackInStock(InventoryRepository inventoryRepository,
                       ProductVariantRepository productVariantRepository,
                       WishlistItemRepository wishlistRepository,
                       NotificationService notificationService) {
        this.inventoryRepository = inventoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.wishlistRepository = wishlistRepository;
        this.notificationService = notificationService;
    }

    /**
     * Sản phẩm của biến thể đang hết hàng: mọi phân loại đều theo dõi kho và đều về 0 (cùng quy tắc "còn hàng" của
     * danh sách sản phẩm). Sản phẩm có phân loại không theo dõi kho thì luôn bán được, không tính là hết.
     */
    public boolean soldOut(ProductVariant variant) {
        Product product = variant == null ? null : variant.getProduct();
        if (product == null || product.getId() == null) {
            return false;
        }
        List<Long> ids = List.of(product.getId());
        List<InventoryRepository.ProductStock> stock = inventoryRepository.findProductStock(ids);
        if (stock.isEmpty()) {
            return false;
        }
        long tracked = stock.getFirst().getTracked() == null ? 0 : stock.getFirst().getTracked();
        long available = stock.getFirst().getAvailable() == null ? 0 : stock.getFirst().getAvailable();
        long total = productVariantRepository.countByProducts(ids).stream()
                .mapToLong(c -> c.getTotal() == null ? 0 : c.getTotal())
                .sum();
        return tracked > 0 && available == 0 && total <= tracked;
    }

    /** Gọi sau khi cộng kho; {@code wasSoldOut} là kết quả {@link #soldOut} đo ngay trước khi cộng. */
    public void afterIncrease(ProductVariant variant, boolean wasSoldOut) {
        if (!wasSoldOut || soldOut(variant)) {
            return;
        }
        Product product = variant.getProduct();
        if (!ProductServiceImpl.isPublic(product)) {
            return;
        }
        NotificationMessages.Message message = NotificationMessages.backInStock(product);
        for (WishlistItem item : wishlistRepository.findAllByProduct_Id(product.getId())) {
            notificationService.notify(item.getUser(), NotificationType.BACK_IN_STOCK, message,
                    AppLinks.product(product.getId()), product.getId());
        }
    }
}
