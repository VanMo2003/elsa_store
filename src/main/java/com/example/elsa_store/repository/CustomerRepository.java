
package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUser_Id(Long userId);

    /** Khách của một cửa hàng: đã từng mua ở đó hoặc là khách vãng lai do cửa hàng tạo. */
    @Query("""
        select distinct c from Customer c
        where c.store.id = :storeId
           or c.id in (select o.customer.id from Order o where o.store.id = :storeId and o.customer is not null)
        order by c.id desc
    """)
    List<Customer> findAllOfStore(@Param("storeId") Long storeId);

    /** Tìm khách theo số điện thoại (ưu tiên số trên hồ sơ, sau đó số của tài khoản). */
    @Query("""
        select c from Customer c left join c.user u
        where c.phone = :phone or u.phone = :phone
    """)
    List<Customer> findAllByPhone(@Param("phone") String phone);
}
