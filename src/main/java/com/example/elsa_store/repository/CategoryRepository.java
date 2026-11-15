
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Danh mục riêng của một cửa hàng. */
    java.util.List<Category> findAllByStore_Id(Long storeId);

    /** Danh mục của mọi cửa hàng đang hiện với khách (đã duyệt, không bị đình chỉ) — để gộp theo tên cho trang chủ. */
    @org.springframework.data.jpa.repository.Query("""
        select c from Category c join c.store s
        where s.approvalStatus = com.example.elsa_store.constant.ApprovalStatus.APPROVED
          and (s.suspended is null or s.suspended = false)
    """)
    java.util.List<Category> findAllOfVisibleStores();
}
