
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findAllByProduct_Id(Long productId);

    List<ProductImage> findAllByProduct_IdOrderByIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProduct_Id(Long id, Long productId);

    long countByProduct_Id(Long productId);
}
