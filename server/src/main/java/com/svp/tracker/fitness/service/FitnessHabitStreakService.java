package com.svp.tracker.fitness.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.domain.FitnessHabitStreakMark;
import com.svp.tracker.fitness.domain.FitnessHabitStreakWindow;
import com.svp.tracker.fitness.dto.FitnessHabitStreakBoardDto;
import com.svp.tracker.fitness.dto.FitnessHabitStreakDayDto;
import com.svp.tracker.fitness.dto.FitnessHabitStreakHabitDto;
import com.svp.tracker.fitness.repository.FitnessHabitStreakMarkRepository;
import com.svp.tracker.fitness.repository.FitnessHabitStreakWindowRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FitnessHabitStreakService {

    public static final int DAY_COUNT = 50;
    public static final String KIND_EXERCISE = "EXERCISE_20MIN";
    public static final String KIND_STUDY = "STUDY_2HR";
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");

    private final CurrentUserService currentUser;
    private final FitnessHabitStreakWindowRepository windowRepository;
    private final FitnessHabitStreakMarkRepository markRepository;

    @Transactional
    public FitnessHabitStreakBoardDto board() {
        long uid = currentUser.requireUserId();
        FitnessHabitStreakWindow window = ensureWindow(uid);
        return toBoard(uid, window);
    }

    @Transactional
    public FitnessHabitStreakBoardDto toggle(String habitKind, LocalDate activityDate) {
        long uid = currentUser.requireUserId();
        String kind = normalizeKind(habitKind);
        FitnessHabitStreakWindow window = ensureWindow(uid);
        LocalDate start = window.getStartDate();
        LocalDate end = start.plusDays(window.getDayCount() - 1L);
        LocalDate today = LocalDate.now(ZONE);
        if (activityDate == null || activityDate.isBefore(start) || activityDate.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is outside the 50-day window");
        }
        if (activityDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark a future day");
        }
        markRepository
                .findByOwnerUserIdAndHabitKindAndActivityDate(uid, kind, activityDate)
                .ifPresentOrElse(
                        markRepository::delete,
                        () -> {
                            FitnessHabitStreakMark mark = new FitnessHabitStreakMark();
                            mark.setOwnerUserId(uid);
                            mark.setHabitKind(kind);
                            mark.setActivityDate(activityDate);
                            mark.setCreatedAt(Instant.now());
                            markRepository.save(mark);
                        });
        return toBoard(uid, window);
    }

    private FitnessHabitStreakWindow ensureWindow(long uid) {
        return windowRepository
                .findByOwnerUserId(uid)
                .orElseGet(() -> {
                    FitnessHabitStreakWindow w = new FitnessHabitStreakWindow();
                    w.setOwnerUserId(uid);
                    w.setStartDate(LocalDate.now(ZONE).minusDays(1));
                    w.setDayCount(DAY_COUNT);
                    w.setCreatedAt(Instant.now());
                    return windowRepository.save(w);
                });
    }

    private FitnessHabitStreakBoardDto toBoard(long uid, FitnessHabitStreakWindow window) {
        LocalDate start = window.getStartDate();
        int count = window.getDayCount();
        LocalDate end = start.plusDays(count - 1L);
        LocalDate today = LocalDate.now(ZONE);
        Set<String> marks = markRepository.findByOwnerUserIdAndActivityDateBetween(uid, start, end).stream()
                .map(m -> m.getHabitKind() + "|" + m.getActivityDate())
                .collect(Collectors.toSet());
        return new FitnessHabitStreakBoardDto(
                start,
                end,
                count,
                today,
                List.of(
                        habit(KIND_EXERCISE, "20 min exercise", "Min 20 minutes of exercise that day", start, count, today, marks),
                        habit(
                                KIND_STUDY,
                                "2 hr self & studies",
                                "2 hours of self-realization and work studies that day",
                                start,
                                count,
                                today,
                                marks)));
    }

    private static FitnessHabitStreakHabitDto habit(
            String kind,
            String title,
            String subtitle,
            LocalDate start,
            int count,
            LocalDate today,
            Set<String> marks) {
        List<FitnessHabitStreakDayDto> days = new ArrayList<>(count);
        int completed = 0;
        for (int i = 0; i < count; i++) {
            LocalDate date = start.plusDays(i);
            boolean done = marks.contains(kind + "|" + date);
            if (done) {
                completed++;
            }
            days.add(new FitnessHabitStreakDayDto(i + 1, date, done, date.equals(today), date.isAfter(today)));
        }
        return new FitnessHabitStreakHabitDto(kind, title, subtitle, completed, days);
    }

    private static String normalizeKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "habitKind is required");
        }
        String k = raw.trim().toUpperCase(Locale.ROOT);
        if (!KIND_EXERCISE.equals(k) && !KIND_STUDY.equals(k)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "habitKind must be EXERCISE_20MIN or STUDY_2HR");
        }
        return k;
    }
}
