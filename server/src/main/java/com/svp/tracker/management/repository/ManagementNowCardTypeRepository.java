package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementNowCardType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementNowCardTypeRepository extends JpaRepository<ManagementNowCardType, Long> {

    List<ManagementNowCardType> findByOwnerUserIdOrderBySortIndexAscIdAsc(Long ownerUserId);

    Optional<ManagementNowCardType> findByOwnerUserIdAndSlug(Long ownerUserId, String slug);

    boolean existsByOwnerUserIdAndSlug(Long ownerUserId, String slug);
}
