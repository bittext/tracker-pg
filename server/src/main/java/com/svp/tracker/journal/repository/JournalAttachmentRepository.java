package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalAttachmentRepository extends JpaRepository<JournalAttachment, Long> {

    List<JournalAttachment> findByEntry_IdIn(Collection<Long> entryIds);

    @Query("SELECT a.entry.id, COUNT(a) FROM JournalAttachment a WHERE a.entry.id IN :ids GROUP BY a.entry.id")
    List<Object[]> countByEntryIdIn(@Param("ids") Set<Long> ids);
}
