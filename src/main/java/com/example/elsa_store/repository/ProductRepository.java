
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Product;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findAllByCategory_Id(Long categoryId);

    List<Product> findAllByStore_Id(Long storeId);

    /** Sản phẩm đang bán của các cửa hàng đã duyệt — danh sách công khai cho khách. */
    @Query("""
        select p from Product p
        where (p.status is null or p.status = 1)
          and (p.store is null
               or p.store.approvalStatus = com.example.elsa_store.constant.ApprovalStatus.APPROVED
               or p.store.approvalStatus is null)
          and (:categoryId is null or p.category.id = :categoryId)
          and (:storeId is null or p.store.id = :storeId)
        order by p.id desc
    """)
    List<Product> findPublic(@Param("categoryId") Long categoryId, @Param("storeId") Long storeId);

    long countByStore_Id(Long storeId);
}
