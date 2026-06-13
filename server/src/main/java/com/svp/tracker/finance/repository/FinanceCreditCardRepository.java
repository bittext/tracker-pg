package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCreditCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCreditCardRepository extends JpaRepository<FinanceCreditCard, Long> {

    List<FinanceCreditCard> findByOwnerUserIdOrderByInstitutionAscCardNameAsc(long ownerUserId);

    Optional<FinanceCreditCard> findByIdAndOwnerUserId(long id, long ownerUserId);
}
