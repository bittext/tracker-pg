package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalCourseRepository extends JpaRepository<JournalCourse, Long> {

    @Query(
            "SELECT c FROM JournalCourse c WHERE c.ownerUserId = :ownerId "
                    + "AND (:status IS NULL OR c.status = :status) "
                    + "AND (:q IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(COALESCE(c.provider, '')) LIKE LOWER(CONCAT('%', :q, '%'))) "
                    + "ORDER BY CASE c.status "
                    + "WHEN 'IN_PROGRESS' THEN 0 WHEN 'INTEND' THEN 1 ELSE 2 END, c.updatedAt DESC, c.id DESC")
    List<JournalCourse> search(
            @Param("ownerId") long ownerId, @Param("status") String status, @Param("q") String q);
}
