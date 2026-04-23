package com.svp.tracker.reportcal.repository;

import com.svp.tracker.reportcal.domain.ReportCalendarEntry;
import com.svp.tracker.reportcal.domain.ReportCalendarType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCalendarEntryRepository extends JpaRepository<ReportCalendarEntry, Long> {

    List<ReportCalendarEntry> findByOwnerUserIdAndCalendarTypeAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            long ownerUserId, ReportCalendarType type, LocalDate from, LocalDate to);
}
