package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingPlaidBalanceSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingPlaidBalanceSnapshotRepository extends JpaRepository<BankingPlaidBalanceSnapshot, Long> {

    Optional<BankingPlaidBalanceSnapshot> findFirstByOwnerUserIdAndInstitutionIdOrderBySnapshotDateDesc(
            long ownerUserId, long institutionId);

    Optional<BankingPlaidBalanceSnapshot> findByOwnerUserIdAndInstitutionIdAndSnapshotDateAndPlaidAccountId(
            long ownerUserId, long institutionId, LocalDate snapshotDate, String plaidAccountId);

    List<BankingPlaidBalanceSnapshot> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateAscInstitutionIdAsc(
            long ownerUserId, LocalDate from, LocalDate to);
}
