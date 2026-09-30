package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.domain.ManagementDueItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDueItemRepository extends JpaRepository<ManagementDueItem, Long> {

    List<ManagementDueItem> findByOwnerUserIdAndActiveTrueOrderByCounterpartyAscIdAsc(long ownerUserId);

    List<ManagementDueItem> findByOwnerUserIdAndDeskAndActiveTrueOrderByCounterpartyAscIdAsc(
            long ownerUserId, ManagementDesk desk);

    Optional<ManagementDueItem> findByIdAndOwnerUserId(long id, long ownerUserId);
}
