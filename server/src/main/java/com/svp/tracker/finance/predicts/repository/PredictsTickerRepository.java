package com.svp.tracker.finance.predicts.repository;

import com.svp.tracker.finance.predicts.domain.PredictsTicker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictsTickerRepository extends JpaRepository<PredictsTicker, Long> {

    List<PredictsTicker> findByOwnerUserIdOrderByAutoSeededAscSymbolAsc(Long ownerUserId);

    Optional<PredictsTicker> findByOwnerUserIdAndSymbol(Long ownerUserId, String symbol);

    long countByOwnerUserIdAndAutoSeededFalse(Long ownerUserId);

    boolean existsByOwnerUserIdAndSymbol(Long ownerUserId, String symbol);
}
