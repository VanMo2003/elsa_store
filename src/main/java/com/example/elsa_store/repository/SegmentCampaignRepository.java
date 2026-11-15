package com.example.elsa_store.repository;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.entity.SegmentCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SegmentCampaignRepository extends JpaRepository<SegmentCampaign, Long> {

    boolean existsByStore_IdAndSegmentAndCreatedAtAfter(Long storeId, CustomerSegment segment, LocalDateTime after);

    List<SegmentCampaign> findTop20ByStore_IdOrderByIdDesc(Long storeId);
}
