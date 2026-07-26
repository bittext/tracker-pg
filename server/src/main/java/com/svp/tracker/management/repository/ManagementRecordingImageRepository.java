package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementRecordingImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementRecordingImageRepository extends JpaRepository<ManagementRecordingImage, Long> {

    List<ManagementRecordingImage> findByRecordingIdOrderBySortOrderAscIdAsc(long recordingId);

    @Query(
            """
            select max(i.sortOrder) from ManagementRecordingImage i
            where i.recording.id = :recordingId
            """)
    Integer findMaxSortOrder(@Param("recordingId") long recordingId);

    @Query(
            """
            select i from ManagementRecordingImage i
            join fetch i.recording r
            where i.id = :id and i.ownerUserId = :owner
            """)
    Optional<ManagementRecordingImage> findByIdAndOwner(
            @Param("id") long id, @Param("owner") long ownerUserId);
}
