package com.svp.tracker.markets.repository;

import com.svp.tracker.markets.domain.MarketsAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketsAuditLogRepository extends JpaRepository<MarketsAuditLog, Long> {}
