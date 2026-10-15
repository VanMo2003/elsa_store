package com.example.elsa_store.repository;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwner_Id(Long ownerId);

    Page<Store> findAllByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    /** Cửa hàng đang hoạt động — hiện công khai cho khách. */
    @Query("select s from Store s where s.approvalStatus = com.example.elsa_store.constant.ApprovalStatus.APPROVED"
            + " or s.approvalStatus is null")
    Page<Store> findAllApproved(Pageable pageable);
}
