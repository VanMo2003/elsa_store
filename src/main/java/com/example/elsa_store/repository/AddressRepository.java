package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Địa chỉ mặc định lên đầu, rồi địa chỉ mới nhất. */
    @Query("select a from Address a where a.customer.id = :customerId"
            + " order by case when a.isDefault = true then 0 else 1 end, a.id desc")
    List<Address> findAllOfCustomer(@Param("customerId") Long customerId);

    long countByCustomer_Id(Long customerId);

    /** Mỗi khách chỉ một địa chỉ mặc định. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.isDefault = false where a.customer.id = :customerId and a.id <> :keepId")
    int clearDefaultExcept(@Param("customerId") Long customerId, @Param("keepId") Long keepId);
}
