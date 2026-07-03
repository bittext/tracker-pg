package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticBankingTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticBankingTransactionRepository
        extends JpaRepository<RobinhoodAgenticBankingTransaction, Long> {

    List<RobinhoodAgenticBankingTransaction> findTop50ByOwnerUserIdOrderByTransactionAtDescIdDesc(long ownerUserId);

    void deleteAllByOwnerUserId(long ownerUserId);
}
