package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findAllByProduct_Id(Long productId);

    /** Số biến thể của từng sản phẩm — để biết sản phẩm có biến thể nào không theo dõi kho (luôn bán được). */
    @Query("select v.product.id as productId, count(v.id) as total from ProductVariant v"
            + " where v.product.id in :productIds group by v.product.id")
    List<VariantCount> countByProducts(@Param("productIds") Collection<Long> productIds);

    long countByProduct_Id(Long productId);

    /** SKU đã có (so khớp theo collation của MySQL — không phân biệt hoa thường). */
    boolean existsBySku(String sku);

    interface VariantCount {
        Long getProductId();

        Long getTotal();
    }

    /**
     * Danh sách kho của một cửa hàng. {@code keyword} đã ở dạng "%...%" chữ thường (null = không lọc). {@code filter}:
     * ALL, TRACKED, UNTRACKED (chưa theo dõi kho), LOW (còn hàng nhưng không vượt mức tối thiểu), OUT (hết hàng).
     */
    @Query(value = """
        select v from ProductVariant v
        join v.product p
        left join v.inventory i
        where p.store.id = :storeId
          and (:keyword is null
               or lower(p.name) like :keyword
               or lower(coalesce(v.sku, '')) like :keyword
               or lower(coalesce(v.color, '')) like :keyword
               or lower(coalesce(v.size, '')) like :keyword)
          and (:filter = 'ALL'
               or (:filter = 'TRACKED' and i.id is not null)
               or (:filter = 'UNTRACKED' and i.id is null)
               or (:filter = 'OUT' and i.id is not null and coalesce(i.quantity, 0) <= 0)
               or (:filter = 'LOW' and i.id is not null and coalesce(i.quantity, 0) > 0
                   and coalesce(i.quantity, 0) <= coalesce(i.minQuantity, 0)))
        order by p.name asc, v.id asc
    """, countQuery = """
        select count(v) from ProductVariant v
        join v.product p
        left join v.inventory i
        where p.store.id = :storeId
          and (:keyword is null
               or lower(p.name) like :keyword
               or lower(coalesce(v.sku, '')) like :keyword
               or lower(coalesce(v.color, '')) like :keyword
               or lower(coalesce(v.size, '')) like :keyword)
          and (:filter = 'ALL'
               or (:filter = 'TRACKED' and i.id is not null)
               or (:filter = 'UNTRACKED' and i.id is null)
               or (:filter = 'OUT' and i.id is not null and coalesce(i.quantity, 0) <= 0)
               or (:filter = 'LOW' and i.id is not null and coalesce(i.quantity, 0) > 0
                   and coalesce(i.quantity, 0) <= coalesce(i.minQuantity, 0)))
    """)
    Page<ProductVariant> searchStock(@Param("storeId") Long storeId,
                                     @Param("keyword") String keyword,
                                     @Param("filter") String filter,
                                     Pageable pageable);

    /**
     * Tổng quan kho của cửa hàng: số biến thể, chưa theo dõi, hết hàng, sắp hết, tổng số lượng đang có, giá trị tồn theo
     * giá vốn, số biến thể còn hàng mà chưa có giá vốn, giá trị tồn theo giá bán.
     */
    @Query("""
        select count(v.id),
               coalesce(sum(case when i.id is null then 1 else 0 end), 0),
               coalesce(sum(case when i.id is not null and coalesce(i.quantity, 0) <= 0 then 1 else 0 end), 0),
               coalesce(sum(case when i.id is not null and coalesce(i.quantity, 0) > 0
                                  and coalesce(i.quantity, 0) <= coalesce(i.minQuantity, 0) then 1 else 0 end), 0),
               coalesce(sum(case when i.id is not null and i.quantity > 0 then i.quantity else 0 end), 0),
               coalesce(sum(case when i.id is not null and i.quantity > 0 and v.costPrice is not null
                                 then i.quantity * v.costPrice else 0.0 end), 0.0),
               coalesce(sum(case when i.id is not null and i.quantity > 0 and v.costPrice is null then 1 else 0 end), 0),
               coalesce(sum(case when i.id is not null and i.quantity > 0
                                 then i.quantity * coalesce(v.price, 0.0) else 0.0 end), 0.0)
        from ProductVariant v
        join v.product p
        left join v.inventory i
        where p.store.id = :storeId
    """)
    List<Object[]> stockOverview(@Param("storeId") Long storeId);
}
