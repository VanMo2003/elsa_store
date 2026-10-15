package com.example.elsa_store.repository;

import com.example.elsa_store.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Thẻ kho của một biến thể, mới nhất trước. */
    Page<StockMovement> findByProductVariant_IdOrderByIdDesc(Long variantId, Pageable pageable);
}
