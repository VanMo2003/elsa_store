package com.example.elsa_store.repository;

import com.example.elsa_store.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByStore_Id(Long storeId);

    /** Phiếu nhập mới nhất trước. {@code storeId} / {@code status} null = không lọc. */
    @Query("""
        select p from PurchaseOrder p
        where (:storeId is null or p.store.id = :storeId)
          and (:status is null or p.status = :status)
        order by p.id desc
    """)
    List<PurchaseOrder> search(@Param("storeId") Long storeId, @Param("status") Integer status);

    /** Khoá phiếu khi nhập kho / huỷ: bấm hai lần cùng lúc cũng chỉ cộng kho một lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCode(String code);
}
