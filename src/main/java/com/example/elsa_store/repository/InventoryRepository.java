
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Inventory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductVariant_Id(Long variantId);

    List<Inventory> findAllByProductVariant_Product_Store_Id(Long storeId);

    /** Biến thể sắp hết hàng: tồn kho không vượt quá mức tối thiểu. {@code storeId} null = toàn hệ thống. */
    @Query("""
        select i from Inventory i
        where coalesce(i.quantity, 0) <= coalesce(i.minQuantity, 0)
          and (:storeId is null or i.productVariant.product.store.id = :storeId)
        order by coalesce(i.quantity, 0) asc
    """)
    List<Inventory> findLowStock(@Param("storeId") Long storeId);

    /**
     * Trừ kho nguyên tử: chỉ trừ khi còn đủ, trong cùng một câu lệnh (MySQL khoá dòng khi cập nhật), nên hai đơn đặt cùng
     * lúc không thể làm kho âm. Trả số dòng đã cập nhật (0 = không đủ hàng).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        update Inventory i set i.quantity = coalesce(i.quantity, 0) - :quantity
        where i.productVariant.id = :variantId and coalesce(i.quantity, 0) >= :quantity
    """)
    int decrement(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Inventory i set i.quantity = coalesce(i.quantity, 0) + :quantity where i.productVariant.id = :variantId")
    int increment(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    /** Tồn kho hiện tại của nhiều biến thể — một truy vấn cho cả giỏ hàng / đơn. */
    @Query("select i.productVariant.id as variantId, i.quantity as quantity from Inventory i"
            + " where i.productVariant.id in :variantIds")
    List<StockLevel> findStockLevels(@Param("variantIds") Collection<Long> variantIds);

    interface StockLevel {
        Long getVariantId();

        Integer getQuantity();
    }

    /**
     * Tồn kho và mức cảnh báo đọc thẳng từ CSDL. Không dùng entity {@link Inventory}: biến thể đã nạp sẵn tồn kho của
     * nó trong phiên, còn lệnh trừ kho là câu update hàng loạt nên entity vẫn giữ số cũ.
     */
    @Query("select i.productVariant.id as variantId, i.quantity as quantity, i.minQuantity as minQuantity"
            + " from Inventory i where i.productVariant.id in :variantIds")
    List<AlertLevel> findAlertLevels(@Param("variantIds") Collection<Long> variantIds);

    interface AlertLevel {
        Long getVariantId();

        Integer getQuantity();

        Integer getMinQuantity();
    }

    /** Theo từng sản phẩm: bao nhiêu biến thể có theo dõi kho, bao nhiêu biến thể trong đó còn hàng. */
    @Query("""
        select i.productVariant.product.id as productId,
               count(i.id) as tracked,
               sum(case when coalesce(i.quantity, 0) > 0 then 1 else 0 end) as available
        from Inventory i
        where i.productVariant.product.id in :productIds
        group by i.productVariant.product.id
    """)
    List<ProductStock> findProductStock(@Param("productIds") Collection<Long> productIds);

    interface ProductStock {
        Long getProductId();

        Long getTracked();

        Long getAvailable();
    }
}
