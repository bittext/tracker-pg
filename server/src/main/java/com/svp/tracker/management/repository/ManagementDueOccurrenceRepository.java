package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDueOccurrence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementDueOccurrenceRepository extends JpaRepository<ManagementDueOccurrence, Long> {

    @Query(
            """
            SELECT o FROM ManagementDueOccurrence o
            JOIN FETCH o.item
            WHERE o.ownerUserId = :ownerId AND o.year = :year
            """)
    List<ManagementDueOccurrence> findByOwnerAndYearWithItem(
            @Param("ownerId") long ownerId, @Param("year") int year);

    Optional<ManagementDueOccurrence> findByItem_IdAndYearAndMonth(long itemId, int year, int month);
}
