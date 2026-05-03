package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWriteup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementWriteupRepository extends JpaRepository<ManagementWriteup, Long> {

    List<ManagementWriteup> findByOwnerUserIdAndYearOrderByUpdatedAtDesc(long ownerUserId, int year);
}
