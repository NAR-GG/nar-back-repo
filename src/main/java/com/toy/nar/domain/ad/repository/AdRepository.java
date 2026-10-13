package com.toy.nar.domain.ad.repository;

import com.toy.nar.domain.ad.entity.Ad;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdRepository extends JpaRepository<Ad, Long> {

    /** 앱 노출 대상 — 켜져 있고 기간 안인 것. 자리별 필터·정렬은 앱이 아니라 여기서 끝낸다. */
    @Query("""
            select a from Ad a
            where a.active = true
              and (a.startAt is null or a.startAt <= :now)
              and (a.endAt is null or a.endAt > :now)
            order by a.sortOrder asc, a.id desc
            """)
    List<Ad> findLive(@Param("now") LocalDateTime now);

    List<Ad> findAllByOrderByCreatedAtDesc();
}
