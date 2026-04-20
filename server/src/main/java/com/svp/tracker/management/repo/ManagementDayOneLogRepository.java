package com.svp.tracker.management.repo;

import com.svp.tracker.management.domain.ManagementDayOneLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDayOneLogRepository extends JpaRepository<ManagementDayOneLog, Long> {

    Optional<ManagementDayOneLog> findByOwnerUserIdAndLoggedOn(Long ownerUserId, LocalDate loggedOn);

    List<ManagementDayOneLog> findByOwnerUserIdAndLoggedOnBetweenOrderByLoggedOnDesc(
            Long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);

    List<ManagementDayOneLog> findByLoggedOnBetweenOrderByLoggedOnDesc(LocalDate fromInclusive, LocalDate toInclusive);
}
