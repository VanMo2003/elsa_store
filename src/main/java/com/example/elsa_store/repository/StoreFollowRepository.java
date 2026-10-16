package com.example.elsa_store.repository;

import com.example.elsa_store.entity.StoreFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreFollowRepository extends JpaRepository<StoreFollow, Long> {

    boolean existsByUser_IdAndStore_Id(Long userId, Long storeId);

    Optional<StoreFollow> findByUser_IdAndStore_Id(Long userId, Long storeId);

    long countByStore_Id(Long storeId);

    List<StoreFollow> findAllByStore_Id(Long storeId);

    List<StoreFollow> findAllByUser_Id(Long userId);

    Page<StoreFollow> findByUser_IdOrderByIdDesc(Long userId, Pageable pageable);
}
