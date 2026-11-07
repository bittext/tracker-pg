package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementMailDigest;
import com.svp.tracker.management.domain.ManagementMailSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementMailDigestRepository extends JpaRepository<ManagementMailDigest, Long> {

    List<ManagementMailDigest> findTop60ByOwnerUserIdOrderByDigestOnDescCreatedAtDesc(long ownerUserId);

    Optional<ManagementMailDigest> findByOwnerUserIdAndDigestOnAndSlot(long ownerUserId, LocalDate digestOn, ManagementMailSlot slot);
}
