package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDueItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDueItemRepository extends JpaRepository<ManagementDueItem, Long> {

    List<ManagementDueItem> findByOwnerUserIdAndActiveTrueOrderByCounterpartyAscIdAsc(long ownerUserId);

    Optional<ManagementDueItem> findByIdAndOwnerUserId(long id, long ownerUserId);
}
