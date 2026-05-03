package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWriteup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementWriteupRepository extends JpaRepository<ManagementWriteup, Long> {

    @Query("SELECT DISTINCT w FROM ManagementWriteup w LEFT JOIN FETCH w.attachments WHERE w.id = :id")
    Optional<ManagementWriteup> findByIdWithAttachments(@Param("id") long id);

    @Query(
            "SELECT DISTINCT w FROM ManagementWriteup w LEFT JOIN FETCH w.attachments "
                    + "WHERE w.ownerUserId = :ownerId AND w.year = :year ORDER BY w.updatedAt DESC, w.id DESC")
    List<ManagementWriteup> findByOwnerAndYearWithAttachments(
            @Param("ownerId") long ownerId, @Param("year") int year);
}
