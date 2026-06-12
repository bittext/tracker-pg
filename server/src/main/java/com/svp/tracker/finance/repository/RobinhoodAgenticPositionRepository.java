package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodAgenticPositionRepository extends JpaRepository<RobinhoodAgenticPosition, Long> {
    List<RobinhoodAgenticPosition> findByOwnerUserIdOrderBySymbolAsc(long ownerUserId);

    @Modifying
    @Query("delete from RobinhoodAgenticPosition p where p.ownerUserId = :uid")
    void deleteAllByOwnerUserId(@Param("uid") long ownerUserId);
}
