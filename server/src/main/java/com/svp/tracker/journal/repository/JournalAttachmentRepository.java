package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalAttachmentRepository extends JpaRepository<JournalAttachment, Long> {

    List<JournalAttachment> findByEntry_IdIn(Collection<Long> entryIds);
}
