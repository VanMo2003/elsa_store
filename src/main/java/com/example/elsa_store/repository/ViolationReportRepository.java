package com.example.elsa_store.repository;

import com.example.elsa_store.constant.ViolationStatus;
import com.example.elsa_store.entity.ViolationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViolationReportRepository extends JpaRepository<ViolationReport, Long> {

    boolean existsByUser_IdAndProduct_IdAndStatus(Long userId, Long productId, ViolationStatus status);

    long countByStatus(ViolationStatus status);

    long countByProduct_IdAndStatus(Long productId, ViolationStatus status);

    /** Danh sách cho ADMIN, mới nhất trước; {@code status} null = mọi trạng thái. */
    @Query("""
        select v from ViolationReport v
        where (:status is null or v.status = :status)
        order by v.id desc
    """)
    Page<ViolationReport> findForAdmin(@Param("status") ViolationStatus status, Pageable pageable);
}
