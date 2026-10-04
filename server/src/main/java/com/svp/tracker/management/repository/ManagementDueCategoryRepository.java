package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDueCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDueCategoryRepository extends JpaRepository<ManagementDueCategory, Long> {

    List<ManagementDueCategory> findByOwnerUserIdOrderBySortOrderDescNameAsc(long ownerUserId);

    long countByOwnerUserId(long ownerUserId);

    Optional<ManagementDueCategory> findByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);
}
