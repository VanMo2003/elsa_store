package com.example.elsa_store.repository;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.entity.CoinTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {

    @Query("select coalesce(sum(t.amount), 0) from CoinTransaction t where t.user.id = :userId")
    long balanceOf(@Param("userId") Long userId);

    /**
     * Số dư đọc bằng truy vấn khoá ({@code FOR UPDATE}): luôn thấy các dòng vừa được giao dịch khác commit. Đọc thường
     * trong InnoDB (REPEATABLE READ) dùng ảnh chụp từ đầu giao dịch nên hai đơn dùng xu cùng lúc đều thấy số dư cũ.
     */
    @Query(value = "select coalesce(sum(amount), 0) from coin_transactions where user_id = :userId for update",
            nativeQuery = true)
    Number lockedBalanceOf(@Param("userId") Long userId);

    /** Tổng xu (có dấu) của một loại biến động trên một đơn. */
    @Query("select coalesce(sum(t.amount), 0) from CoinTransaction t where t.order.id = :orderId and t.type = :type")
    long sumForOrder(@Param("orderId") Long orderId, @Param("type") CoinTransactionType type);

    boolean existsByOrder_IdAndType(Long orderId, CoinTransactionType type);

    @Query("select coalesce(sum(t.amount), 0) from CoinTransaction t where t.user.id = :userId and t.type = :type")
    long sumForUser(@Param("userId") Long userId, @Param("type") CoinTransactionType type);

    boolean existsByUser_IdAndProduct_IdAndType(Long userId, Long productId, CoinTransactionType type);

    Page<CoinTransaction> findByUser_IdOrderByIdDesc(Long userId, Pageable pageable);

    /** Xoá đơn: giữ sổ xu (mã đơn còn trong ghi chú), chỉ bỏ liên kết tới đơn. */
    @Modifying
    @Query("update CoinTransaction t set t.order = null where t.order.id = :orderId")
    int detachOrder(@Param("orderId") Long orderId);
}
