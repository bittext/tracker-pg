package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalBookRepository extends JpaRepository<JournalBook, Long> {

    @Query(
            "SELECT b FROM JournalBook b WHERE b.ownerUserId = :ownerId "
                    + "AND (:status IS NULL OR b.status = :status) "
                    + "AND (:q IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :q, '%'))) "
                    + "ORDER BY CASE b.status "
                    + "WHEN 'READING' THEN 0 WHEN 'TO_READ' THEN 1 ELSE 2 END, b.updatedAt DESC, b.id DESC")
    List<JournalBook> search(
            @Param("ownerId") long ownerId, @Param("status") String status, @Param("q") String q);
}
