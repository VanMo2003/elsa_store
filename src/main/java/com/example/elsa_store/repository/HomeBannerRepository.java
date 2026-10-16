package com.example.elsa_store.repository;

import com.example.elsa_store.entity.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

    /** Banner đang bật và trong thời gian hiển thị, theo thứ tự. */
    @Query("""
        select b from HomeBanner b
        where b.active = true
          and (b.startsAt is null or b.startsAt <= :now)
          and (b.endsAt is null or b.endsAt > :now)
        order by coalesce(b.sortOrder, 0), b.id desc
    """)
    List<HomeBanner> findShowing(@Param("now") LocalDateTime now);

    @Query("select b from HomeBanner b order by coalesce(b.sortOrder, 0), b.id desc")
    List<HomeBanner> findAllOrdered();
}
