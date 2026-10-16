package com.example.elsa_store.repository;

import com.example.elsa_store.entity.QuickReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickReplyRepository extends JpaRepository<QuickReply, Long> {

    List<QuickReply> findByStore_IdOrderByIdAsc(Long storeId);

    long countByStore_Id(Long storeId);
}
