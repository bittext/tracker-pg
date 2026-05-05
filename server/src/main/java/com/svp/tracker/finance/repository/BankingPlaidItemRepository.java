package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingPlaidItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingPlaidItemRepository extends JpaRepository<BankingPlaidItem, Long> {

    Optional<BankingPlaidItem> findByOwnerUserIdAndInstitution_Id(long ownerUserId, long institutionId);
}
