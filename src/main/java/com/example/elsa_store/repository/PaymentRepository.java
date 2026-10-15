package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByOrder_IdOrderByIdDesc(Long orderId);

    /**
     * Khoá dòng thanh toán: kết quả VNPay về hai đường (trình duyệt quay lại và IPN máy chủ) gần như cùng lúc, chỉ một
     * đường được xử lý — đường kia thấy trạng thái đã chốt.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.transactionCode = :code")
    Optional<Payment> findByTransactionCodeForUpdate(@Param("code") String code);

    List<Payment> findAllByOrder_IdOrderByIdDesc(Long orderId);
}
