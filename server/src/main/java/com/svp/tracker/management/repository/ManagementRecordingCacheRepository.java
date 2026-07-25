package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementRecordingCache;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementRecordingCacheRepository extends JpaRepository<ManagementRecordingCache, Long> {

    Optional<ManagementRecordingCache> findByOwnerUserIdAndRelativePath(long ownerUserId, String relativePath);

    List<ManagementRecordingCache> findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(long ownerUserId);

    Optional<ManagementRecordingCache> findFirstByProcessingStatusOrderByUpdatedAtAsc(String processingStatus);

    List<ManagementRecordingCache> findByProcessingStatusAndProcessingStartedAtBefore(
            String processingStatus, Instant cutoff);

    @Query(
            """
            select c from ManagementRecordingCache c
            where c.ownerUserId = :owner
              and (
                lower(c.displayName) like lower(concat('%', :q, '%'))
                or lower(c.relativePath) like lower(concat('%', :q, '%'))
                or (c.transcript is not null and lower(c.transcript) like lower(concat('%', :q, '%')))
                or (c.summary is not null and lower(c.summary) like lower(concat('%', :q, '%')))
              )
            order by c.recordedDay desc, c.updatedAt desc
            """)
    List<ManagementRecordingCache> search(@Param("owner") long ownerUserId, @Param("q") String q);
}
