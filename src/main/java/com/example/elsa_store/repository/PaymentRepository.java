
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByOrder_IdOrderByIdDesc(Long orderId);
}
