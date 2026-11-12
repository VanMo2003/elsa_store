package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.StoreFollow;
import com.example.elsa_store.repository.StoreFollowRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cửa hàng mở bán sản phẩm mới: khi sản phẩm (đang bán) có phân loại đầu tiên — lúc đó khách mới đặt mua được — người
 * theo dõi cửa hàng nhận thông báo. Thêm phân loại thứ hai trở đi không báo lại.
 */
@Component
public class NewProductAlerts {

    private final StoreFollowRepository followRepository;
    private final NotificationService notificationService;

    public NewProductAlerts(StoreFollowRepository followRepository, NotificationService notificationService) {
        this.followRepository = followRepository;
        this.notificationService = notificationService;
    }

    /**
     * Sau khi nhập hàng loạt từ Excel: một sản phẩm thì báo như thêm tay; nhiều sản phẩm thì mỗi người theo dõi nhận một
     * tin gộp (mở trang cửa hàng) thay vì một tin cho từng sản phẩm.
     */
    public void afterImport(com.example.elsa_store.entity.Store store, java.util.List<Product> created) {
        List<Product> visible = created.stream().filter(ProductServiceImpl::isPublic).toList();
        if (visible.isEmpty() || store == null) {
            return;
        }
        if (visible.size() == 1) {
            afterVariantAdded(visible.getFirst(), 1);
            return;
        }
        NotificationMessages.Message message = NotificationMessages.newProducts(store, visible.size());
        for (StoreFollow follow : followRepository.findAllByStore_Id(store.getId())) {
            notificationService.notify(follow.getUser(), NotificationType.NEW_PRODUCT, message,
                    AppLinks.store(store.getId()), store.getId());
        }
    }

    /** {@code variantCount}: số phân loại của sản phẩm sau khi vừa thêm một phân loại. */
    public void afterVariantAdded(Product product, long variantCount) {
        if (variantCount != 1 || product.getStore() == null || !ProductServiceImpl.isPublic(product)) {
            return;
        }
        NotificationMessages.Message message = NotificationMessages.newProduct(product);
        for (StoreFollow follow : followRepository.findAllByStore_Id(product.getStore().getId())) {
            notificationService.notify(follow.getUser(), NotificationType.NEW_PRODUCT, message,
                    AppLinks.product(product.getId()), product.getId());
        }
    }
}
