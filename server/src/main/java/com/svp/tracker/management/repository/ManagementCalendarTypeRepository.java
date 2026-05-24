package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementCalendarType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementCalendarTypeRepository extends JpaRepository<ManagementCalendarType, Long> {

    List<ManagementCalendarType> findByOwnerUserIdOrderBySortIndexAscIdAsc(Long ownerUserId);

    boolean existsByOwnerUserIdAndCode(Long ownerUserId, String code);

    long countByOwnerUserId(Long ownerUserId);
}
