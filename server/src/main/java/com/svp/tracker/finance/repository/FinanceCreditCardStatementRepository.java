package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCreditCardStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCreditCardStatementRepository extends JpaRepository<FinanceCreditCardStatement, Long> {

    List<FinanceCreditCardStatement> findByCreditCardIdAndOwnerUserIdOrderByStatementDateDesc(long creditCardId, long ownerUserId);

    Optional<FinanceCreditCardStatement> findByIdAndCreditCardIdAndOwnerUserId(long id, long creditCardId, long ownerUserId);
}
