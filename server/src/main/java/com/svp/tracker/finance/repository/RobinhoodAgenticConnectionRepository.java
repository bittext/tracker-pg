package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticConnectionRepository extends JpaRepository<RobinhoodAgenticConnection, Long> {
    Optional<RobinhoodAgenticConnection> findByOwnerUserId(long ownerUserId);

    void deleteByOwnerUserId(long ownerUserId);
}
