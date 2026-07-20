package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalBookRepository extends JpaRepository<JournalBook, Long> {

    List<JournalBook> findByOwnerUserIdOrderByUpdatedAtDescIdDesc(long ownerUserId);
}
