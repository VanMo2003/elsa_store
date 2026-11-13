package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ReviewDigest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewDigestRepository extends JpaRepository<ReviewDigest, Long> {

    Optional<ReviewDigest> findByProduct_Id(Long productId);
}
