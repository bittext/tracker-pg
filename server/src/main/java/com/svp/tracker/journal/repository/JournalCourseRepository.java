package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalCourseRepository extends JpaRepository<JournalCourse, Long> {

    List<JournalCourse> findByOwnerUserIdOrderByUpdatedAtDescIdDesc(long ownerUserId);
}
