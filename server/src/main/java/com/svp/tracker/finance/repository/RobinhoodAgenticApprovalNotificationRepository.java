package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticApprovalNotification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticApprovalNotificationRepository
        extends JpaRepository<RobinhoodAgenticApprovalNotification, Long> {
    List<RobinhoodAgenticApprovalNotification> findTop30ByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant since);
}
