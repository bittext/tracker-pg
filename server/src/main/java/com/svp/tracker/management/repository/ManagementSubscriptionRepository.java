package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.domain.ManagementSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementSubscriptionRepository extends JpaRepository<ManagementSubscription, Long> {

    List<ManagementSubscription> findByOwnerUserIdAndDeskOrderByNameAscIdAsc(long ownerUserId, ManagementDesk desk);

    Optional<ManagementSubscription> findByIdAndOwnerUserId(long id, long ownerUserId);
}
