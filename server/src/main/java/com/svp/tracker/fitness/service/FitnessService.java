package com.svp.tracker.fitness.service;

import com.svp.tracker.fitness.domain.BodyWeightLog;
import com.svp.tracker.fitness.domain.Exercise;
import com.svp.tracker.fitness.domain.ExerciseDayLog;
import com.svp.tracker.fitness.dto.DailyExerciseLogLineDto;
import com.svp.tracker.fitness.dto.DailyExerciseReportDto;
import com.svp.tracker.fitness.dto.ExerciseDayBreakdownDto;
import com.svp.tracker.fitness.dto.ExerciseMonthBreakdownDto;
import com.svp.tracker.fitness.dto.MonthActivityCalendarDto;
import com.svp.tracker.fitness.dto.MonthlyExerciseReportDto;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.fitness.repository.BodyWeightLogRepository;
import com.svp.tracker.fitness.repository.ExerciseDayLogRepository;
import com.svp.tracker.fitness.repository.ExerciseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FitnessService {

    private static final BigDecimal LB_PER_KG = new BigDecimal("2.2046226218");

    private final ExerciseRepository exerciseRepository;
    private final ExerciseDayLogRepository exerciseDayLogRepository;
    private final BodyWeightLogRepository bodyWeightLogRepository;

    public List<Exercise> listExercises() {
        return exerciseRepository.findAll();
    }

    public Exercise getExercise(Long id) {
        return exerciseRepository.findById(id).orElseThrow(() -> new NotFoundException("Exercise not found: " + id));
    }

    @Transactional
    public Exercise createExercise(Exercise exercise) {
        return exerciseRepository.save(exercise);
    }

    @Transactional
    public Exercise updateExercise(Long id, Exercise patch) {
        Exercise e = getExercise(id);
        if (patch.getName() != null) {
            e.setName(patch.getName());
        }
        if (patch.getCategory() != null) {
            e.setCategory(patch.getCategory());
        }
        if (patch.getNotes() != null) {
            e.setNotes(patch.getNotes());
        }
        return exerciseRepository.save(e);
    }

    @Transactional
    public void deleteExercise(Long id) {
        exerciseDayLogRepository.deleteByExercise_Id(id);
        exerciseRepository.deleteById(id);
    }

    public List<ExerciseDayLog> listDayLogsForDay(Long exerciseId, LocalDate day) {
        return exerciseDayLogRepository.findByExerciseIdAndPerformedOnOrderByIdAsc(exerciseId, day);
    }

    public List<ExerciseDayLog> listDayLogsBetween(LocalDate from, LocalDate to) {
        return exerciseDayLogRepository.findByPerformedOnBetweenOrderByPerformedOnAscIdAsc(from, to);
    }

    @Transactional
    public ExerciseDayLog addDayLog(Long exerciseId, ExerciseDayLog row) {
        Exercise ex = getExercise(exerciseId);
        row.setExercise(ex);
        return exerciseDayLogRepository.save(row);
    }

    @Transactional
    public ExerciseDayLog updateDayLog(Long id, ExerciseDayLog patch) {
        ExerciseDayLog row =
                exerciseDayLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Day log not found: " + id));
        if (patch.getPerformedOn() != null) {
            row.setPerformedOn(patch.getPerformedOn());
        }
        if (patch.getNotes() != null) {
            row.setNotes(patch.getNotes());
        }
        if (patch.getDurationMinutes() != null) {
            row.setDurationMinutes(patch.getDurationMinutes());
        }
        return exerciseDayLogRepository.save(row);
    }

    @Transactional
    public void deleteDayLog(Long id) {
        exerciseDayLogRepository.deleteById(id);
    }

    public List<BodyWeightLog> listBodyWeight() {
        return bodyWeightLogRepository.findAllByOrderByLoggedOnDesc();
    }

    @Transactional
    public BodyWeightLog logBodyWeight(BodyWeightLog log) {
        if (log.getWeightLb() != null) {
            log.setWeightLb(log.getWeightLb().setScale(3, RoundingMode.HALF_UP));
        }
        if (log.getWeightKg() != null) {
            log.setWeightKg(log.getWeightKg().setScale(3, RoundingMode.HALF_UP));
        }
        ensureWeightLb(log);
        return bodyWeightLogRepository.save(log);
    }

    @Transactional
    public BodyWeightLog updateBodyWeight(Long id, BodyWeightLog patch) {
        BodyWeightLog b = bodyWeightLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Body weight log not found: " + id));
        if (patch.getLoggedOn() != null) {
            b.setLoggedOn(patch.getLoggedOn());
        }
        if (patch.getWeightKg() != null) {
            b.setWeightKg(patch.getWeightKg());
        }
        if (patch.getWeightLb() != null) {
            b.setWeightLb(patch.getWeightLb());
        }
        if (patch.getNotes() != null) {
            b.setNotes(patch.getNotes());
        }
        ensureWeightLb(b);
        return bodyWeightLogRepository.save(b);
    }

    /** If {@code weight_lb} was not sent (legacy clients), derive it from kg. */
    private static void ensureWeightLb(BodyWeightLog log) {
        if (log.getWeightLb() == null && log.getWeightKg() != null) {
            log.setWeightLb(log.getWeightKg().multiply(LB_PER_KG).setScale(3, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public void deleteBodyWeight(Long id) {
        bodyWeightLogRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DailyExerciseReportDto dailyReport(LocalDate date) {
        List<ExerciseDayLog> logs =
                exerciseDayLogRepository.findByPerformedOnBetweenOrderByPerformedOnAscIdAsc(date, date);
        DailyExerciseReportDto dto = new DailyExerciseReportDto();
        dto.setDate(date);
        dto.setTotalLogs(logs.size());
        Map<Long, DayAgg> byExercise = new HashMap<>();
        for (ExerciseDayLog l : logs) {
            Exercise ex = l.getExercise();
            if (ex == null || ex.getId() == null) {
                continue;
            }
            DayAgg agg = byExercise.computeIfAbsent(ex.getId(), id -> new DayAgg(ex.getId(), ex.getName()));
            agg.logCount++;
        }
        bodyWeightLogRepository.findFirstByLoggedOn(date).ifPresent(b -> dto.setBodyWeightKg(b.getWeightKg()));
        List<ExerciseDayBreakdownDto> rows = byExercise.values().stream()
                .sorted(Comparator.comparing(a -> a.name))
                .map(a -> new ExerciseDayBreakdownDto(a.exerciseId, a.name, a.logCount))
                .toList();
        dto.setExercises(new ArrayList<>(rows));

        List<DailyExerciseLogLineDto> lines = new ArrayList<>();
        for (ExerciseDayLog l : logs) {
            Exercise ex = l.getExercise();
            if (ex == null || ex.getId() == null) {
                continue;
            }
            lines.add(new DailyExerciseLogLineDto(
                    l.getId(), ex.getId(), ex.getName(), l.getNotes(), l.getDurationMinutes()));
        }
        dto.setLogLines(lines);
        return dto;
    }

    @Transactional(readOnly = true)
    public MonthlyExerciseReportDto monthlyReport(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ExerciseDayLog> logs =
                exerciseDayLogRepository.findByPerformedOnBetweenOrderByPerformedOnAscIdAsc(from, to);
        MonthlyExerciseReportDto dto = new MonthlyExerciseReportDto();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setTotalLogs(logs.size());
        int distinctExerciseLogDays =
                (int) logs.stream().map(ExerciseDayLog::getPerformedOn).distinct().count();
        dto.setWorkoutDays(distinctExerciseLogDays);
        dto.setExerciseLogActiveDays(distinctExerciseLogDays);
        Map<Long, MonthAgg> byExercise = new HashMap<>();
        for (ExerciseDayLog l : logs) {
            Exercise ex = l.getExercise();
            if (ex == null || ex.getId() == null) {
                continue;
            }
            MonthAgg agg = byExercise.computeIfAbsent(ex.getId(), id -> new MonthAgg(ex.getId(), ex.getName()));
            agg.logCount++;
            agg.days.add(l.getPerformedOn());
        }
        List<ExerciseMonthBreakdownDto> rows = byExercise.values().stream()
                .sorted(Comparator.comparing(a -> a.name))
                .map(a -> new ExerciseMonthBreakdownDto(
                        a.exerciseId, a.name, a.logCount, a.days.size()))
                .toList();
        dto.setExercises(new ArrayList<>(rows));
        return dto;
    }

    @Transactional(readOnly = true)
    public MonthActivityCalendarDto monthActivityCalendar(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ExerciseDayLog> logRows = exerciseDayLogRepository.findByPerformedOnBetweenOrderByPerformedOnAscIdAsc(from, to);
        List<BodyWeightLog> weightRows = bodyWeightLogRepository.findByLoggedOnBetweenOrderByLoggedOnAsc(from, to);
        List<LocalDate> strength = exerciseDayLogRepository.findDistinctPerformedOnBetween(from, to);
        List<LocalDate> weight = bodyWeightLogRepository.findDistinctLoggedOnBetween(from, to);
        MonthActivityCalendarDto dto = new MonthActivityCalendarDto();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setDaysWithStrengthTraining(strength.stream().map(LocalDate::toString).toList());
        dto.setDaysWithWeightLogged(weight.stream().map(LocalDate::toString).toList());
        // Active days = exercise logs only (not weight-only days); calendar tick uses the same rule.
        dto.setActiveDays(strength.stream().map(LocalDate::toString).sorted().toList());
        Map<String, Integer> exerciseDurationByDay = new HashMap<>();
        for (ExerciseDayLog row : logRows) {
            if (row.getPerformedOn() == null) {
                continue;
            }
            String key = row.getPerformedOn().toString();
            int mins = row.getDurationMinutes() != null ? Math.max(0, row.getDurationMinutes()) : 0;
            exerciseDurationByDay.put(key, exerciseDurationByDay.getOrDefault(key, 0) + mins);
        }
        dto.setExerciseDurationMinutesByDay(exerciseDurationByDay);
        Map<String, BigDecimal> weightByDay = new HashMap<>();
        for (BodyWeightLog row : weightRows) {
            if (row.getLoggedOn() == null || row.getWeightKg() == null) {
                continue;
            }
            // Keep latest row if duplicates exist for the same date.
            weightByDay.put(row.getLoggedOn().toString(), row.getWeightKg());
        }
        dto.setBodyWeightKgByDay(weightByDay);
        return dto;
    }

    private static final class DayAgg {
        private final Long exerciseId;
        private final String name;
        private int logCount;

        private DayAgg(Long exerciseId, String name) {
            this.exerciseId = exerciseId;
            this.name = name;
        }
    }

    private static final class MonthAgg {
        private final Long exerciseId;
        private final String name;
        private int logCount;
        private final Set<LocalDate> days = new HashSet<>();

        private MonthAgg(Long exerciseId, String name) {
            this.exerciseId = exerciseId;
            this.name = name;
        }
    }
}
