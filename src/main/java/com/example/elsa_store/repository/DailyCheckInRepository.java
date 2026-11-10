package com.example.elsa_store.repository;

import com.example.elsa_store.entity.DailyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, Long> {

    /** Lần điểm danh gần nhất của khách. */
    Optional<DailyCheckIn> findFirstByUser_IdOrderByCheckDateDesc(Long userId);

    boolean existsByUser_IdAndCheckDate(Long userId, LocalDate checkDate);
}
