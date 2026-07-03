package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticBankingConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticBankingConnectionRepository
        extends JpaRepository<RobinhoodAgenticBankingConnection, Long> {

    Optional<RobinhoodAgenticBankingConnection> findByOwnerUserId(long ownerUserId);

    void deleteByOwnerUserId(long ownerUserId);
}
