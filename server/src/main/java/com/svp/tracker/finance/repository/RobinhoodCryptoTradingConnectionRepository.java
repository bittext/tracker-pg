package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCryptoTradingConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodCryptoTradingConnectionRepository
        extends JpaRepository<RobinhoodCryptoTradingConnection, Long> {

    Optional<RobinhoodCryptoTradingConnection> findByOwnerUserId(long ownerUserId);

    List<RobinhoodCryptoTradingConnection> findAllByOrderByOwnerUserIdAsc();

    void deleteByOwnerUserId(long ownerUserId);
}
