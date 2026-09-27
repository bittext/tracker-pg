package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhIndividualMarginPeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhIndividualMarginPeekRepository extends JpaRepository<RhIndividualMarginPeek, Long> {

    Optional<RhIndividualMarginPeek> findByOwnerUserIdAndAccountSuffixAndCapturedAt(
            long ownerUserId, String accountSuffix, Instant capturedAt);

    Optional<RhIndividualMarginPeek> findTopByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(
            long ownerUserId, String accountSuffix);

    List<RhIndividualMarginPeek> findByOwnerUserIdAndAccountSuffixAndSnapshotDateBetweenOrderByCapturedAtAsc(
            long ownerUserId, String accountSuffix, LocalDate from, LocalDate to);

    List<RhIndividualMarginPeek> findTop48ByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(
            long ownerUserId, String accountSuffix);

    boolean existsByOwnerUserIdAndAccountSuffix(long ownerUserId, String accountSuffix);
}
