package com.svp.tracker.fitness.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.domain.AppleHealthWeight;
import com.svp.tracker.fitness.domain.AppleHealthWorkout;
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
import com.svp.tracker.fitness.repository.AppleHealthWeightRepository;
import com.svp.tracker.fitness.repository.AppleHealthWorkoutRepository;
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
import java.util.Objects;
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
    private final AppleHealthWorkoutRepository appleWorkoutRepository;
    private final AppleHealthWeightRepository appleWeightRepository;
    private final CurrentUserService currentUser;

    public List<Exercise> listExercises() {
        return exerciseRepository.findByOwnerUserIdOrderByNameAsc(currentUser.requireUserId());
    }

    public Exercise getExercise(Long id) {
        Exercise e = exerciseRepository.findById(id).orElseThrow(() -> new NotFoundException("Exercise not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        return e;
    }

    @Transactional
    public Exercise createExercise(Exercise exercise) {
        exercise.setId(null);
        exercise.setOwnerUserId(currentUser.requireUserId());
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
        getExercise(id);
        exerciseDayLogRepository.deleteByExercise_Id(id);
        exerciseRepository.deleteById(id);
    }

    public List<ExerciseDayLog> listDayLogsForDay(Long exerciseId, LocalDate day) {
        getExercise(exerciseId);
        return exerciseDayLogRepository.findByOwnerUserIdAndExerciseIdAndPerformedOnOrderByIdAsc(
                currentUser.requireUserId(), exerciseId, day);
    }

    public List<ExerciseDayLog> listDayLogsBetween(LocalDate from, LocalDate to) {
        return exerciseDayLogRepository.findByOwnerUserIdAndPerformedOnBetweenOrderByPerformedOnAscIdAsc(
                currentUser.requireUserId(), from, to);
    }

    @Transactional
    public ExerciseDayLog addDayLog(Long exerciseId, ExerciseDayLog row) {
        Exercise ex = getExercise(exerciseId);
        row.setExercise(ex);
        row.setOwnerUserId(ex.getOwnerUserId() != null ? ex.getOwnerUserId() : currentUser.requireUserId());
        return exerciseDayLogRepository.save(row);
    }

    @Transactional
    public ExerciseDayLog updateDayLog(Long id, ExerciseDayLog patch) {
        ExerciseDayLog row =
                exerciseDayLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Day log not found: " + id));
        assertRowAccess(row.getOwnerUserId());
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
        ExerciseDayLog row =
                exerciseDayLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Day log not found: " + id));
        assertRowAccess(row.getOwnerUserId());
        exerciseDayLogRepository.deleteById(id);
    }

    public List<BodyWeightLog> listBodyWeight() {
        return bodyWeightLogRepository.findByOwnerUserIdOrderByLoggedOnDesc(currentUser.requireUserId());
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
        log.setOwnerUserId(currentUser.requireUserId());
        return bodyWeightLogRepository.save(log);
    }

    @Transactional
    public BodyWeightLog updateBodyWeight(Long id, BodyWeightLog patch) {
        BodyWeightLog b =
                bodyWeightLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Body weight log not found: " + id));
        assertRowAccess(b.getOwnerUserId());
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
        BodyWeightLog b =
                bodyWeightLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Body weight log not found: " + id));
        assertRowAccess(b.getOwnerUserId());
        bodyWeightLogRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DailyExerciseReportDto dailyReport(LocalDate date) {
        List<ExerciseDayLog> logs =
                exerciseDayLogRepository.findByOwnerUserIdAndPerformedOnBetweenOrderByPerformedOnAscIdAsc(
                        currentUser.requireUserId(), date, date);
        DailyExerciseReportDto dto = new DailyExerciseReportDto();
        dto.setDate(date);
        dto.setTotalLogs(logs.size());
        Map<Long, DayAgg> byExercise = new HashMap<>();
        for (ExerciseDayLog l : logs) {
            Exercise ex = l.getExercise();
            if (ex == null || ex.getId() == null) {
                continue;
            }
            DayAgg agg = byExercise.computeIfAbsent(ex.getId(), i -> new DayAgg(ex.getId(), ex.getName()));
            agg.logCount++;
        }
        bodyWeightLogRepository
                .findFirstByOwnerUserIdAndLoggedOn(currentUser.requireUserId(), date)
                .ifPresent(b -> dto.setBodyWeightKg(b.getWeightKg()));
        if (dto.getBodyWeightKg() == null) {
            appleWeightKg(currentUser.requireUserId(), date).ifPresent(dto::setBodyWeightKg);
        }
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
        List<AppleHealthWorkout> appleWorkouts =
                appleWorkoutRepository.findByOwnerUserIdAndDayBetweenOrderByStartedAtDesc(
                        currentUser.requireUserId(), date, date);
        for (AppleHealthWorkout workout : appleWorkouts) {
            lines.add(new DailyExerciseLogLineDto(
                    null, null, workout.getActivity(), appleWorkoutNote(workout), workout.getDurationMinutes()));
        }
        dto.setTotalLogs(logs.size() + appleWorkouts.size());
        dto.setLogLines(lines);
        return dto;
    }

    @Transactional(readOnly = true)
    public MonthlyExerciseReportDto monthlyReport(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ExerciseDayLog> logs =
                exerciseDayLogRepository.findByOwnerUserIdAndPerformedOnBetweenOrderByPerformedOnAscIdAsc(
                        currentUser.requireUserId(), from, to);
        MonthlyExerciseReportDto dto = new MonthlyExerciseReportDto();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setTotalLogs(logs.size());
        int distinctExerciseLogDays =
                (int) logs.stream().map(ExerciseDayLog::getPerformedOn).distinct().count();
        dto.setExerciseLogActiveDays(distinctExerciseLogDays);
        Map<Long, MonthAgg> byExercise = new HashMap<>();
        Set<LocalDate> workoutDays = new HashSet<>();
        for (ExerciseDayLog l : logs) {
            if (l.getPerformedOn() != null) {
                workoutDays.add(l.getPerformedOn());
            }
            Exercise ex = l.getExercise();
            if (ex == null || ex.getId() == null) {
                continue;
            }
            MonthAgg agg = byExercise.computeIfAbsent(ex.getId(), i -> new MonthAgg(ex.getId(), ex.getName()));
            agg.logCount++;
            agg.days.add(l.getPerformedOn());
        }
        Map<String, MonthAgg> appleByActivity = new HashMap<>();
        for (AppleHealthWorkout workout : appleWorkoutRepository.findByOwnerUserIdAndDayBetweenOrderByStartedAtDesc(
                currentUser.requireUserId(), from, to)) {
            if (workout.getDay() != null) {
                workoutDays.add(workout.getDay());
            }
            String name = workout.getActivity() == null || workout.getActivity().isBlank()
                    ? "Apple Health"
                    : workout.getActivity();
            MonthAgg agg = appleByActivity.computeIfAbsent(name, n -> new MonthAgg(null, n));
            agg.logCount++;
            if (workout.getDay() != null) {
                agg.days.add(workout.getDay());
            }
        }
        dto.setWorkoutDays(workoutDays.size());
        int appleLogs = appleByActivity.values().stream().mapToInt(a -> a.logCount).sum();
        dto.setTotalLogs(logs.size() + appleLogs);
        List<ExerciseMonthBreakdownDto> rows = new ArrayList<>();
        byExercise.values().stream()
                .sorted(Comparator.comparing(a -> a.name))
                .map(a -> new ExerciseMonthBreakdownDto(a.exerciseId, a.name, a.logCount, a.days.size()))
                .forEach(rows::add);
        appleByActivity.values().stream()
                .sorted(Comparator.comparing(a -> a.name))
                .map(a -> new ExerciseMonthBreakdownDto(null, a.name + " · Apple Health", a.logCount, a.days.size()))
                .forEach(rows::add);
        dto.setExercises(rows);
        return dto;
    }

    @Transactional(readOnly = true)
    public MonthActivityCalendarDto monthActivityCalendar(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ExerciseDayLog> logRows =
                exerciseDayLogRepository.findByOwnerUserIdAndPerformedOnBetweenOrderByPerformedOnAscIdAsc(
                        currentUser.requireUserId(), from, to);
        List<BodyWeightLog> weightRows =
                bodyWeightLogRepository.findByOwnerUserIdAndLoggedOnBetweenOrderByLoggedOnAsc(
                        currentUser.requireUserId(), from, to);
        List<LocalDate> strength =
                exerciseDayLogRepository.findDistinctPerformedOnBetweenForOwner(
                        currentUser.requireUserId(), from, to);
        List<LocalDate> weight =
                bodyWeightLogRepository.findDistinctLoggedOnBetweenForOwner(currentUser.requireUserId(), from, to);
        MonthActivityCalendarDto dto = new MonthActivityCalendarDto();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setDaysWithStrengthTraining(strength.stream().map(LocalDate::toString).toList());
        dto.setDaysWithWeightLogged(weight.stream().map(LocalDate::toString).toList());
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
            weightByDay.put(row.getLoggedOn().toString(), row.getWeightKg());
        }
        long owner = currentUser.requireUserId();
        Set<String> active = new HashSet<>(dto.getActiveDays());
        for (AppleHealthWorkout workout : appleWorkoutRepository.findByOwnerUserIdAndDayBetweenOrderByStartedAtDesc(
                owner, from, to)) {
            if (workout.getDay() == null) {
                continue;
            }
            String key = workout.getDay().toString();
            active.add(key);
            int mins = Math.max(0, workout.getDurationMinutes());
            exerciseDurationByDay.put(key, exerciseDurationByDay.getOrDefault(key, 0) + mins);
        }
        Set<String> weightDays = new HashSet<>(dto.getDaysWithWeightLogged());
        for (AppleHealthWeight sample : appleWeightRepository.findByOwnerUserIdAndLoggedOnBetweenOrderByMeasuredAtDesc(
                owner, from, to)) {
            if (sample.getLoggedOn() == null || sample.getWeightLb() == null) {
                continue;
            }
            String key = sample.getLoggedOn().toString();
            weightDays.add(key);
            weightByDay.putIfAbsent(key, sample.getWeightLb().divide(LB_PER_KG, 3, RoundingMode.HALF_UP));
        }
        List<String> activeDays = active.stream().sorted().toList();
        dto.setActiveDays(activeDays);
        dto.setDaysWithStrengthTraining(activeDays);
        dto.setDaysWithWeightLogged(weightDays.stream().sorted().toList());
        dto.setExerciseDurationMinutesByDay(exerciseDurationByDay);
        dto.setBodyWeightKgByDay(weightByDay);
        return dto;
    }

    /** Latest Apple Health weigh-in that day, in kilograms. Manual logs stay preferred. */
    private java.util.Optional<BigDecimal> appleWeightKg(long owner, LocalDate date) {
        return appleWeightRepository
                .findByOwnerUserIdAndLoggedOnBetweenOrderByMeasuredAtDesc(owner, date, date)
                .stream()
                .map(AppleHealthWeight::getWeightLb)
                .filter(Objects::nonNull)
                .findFirst()
                .map(lb -> lb.divide(LB_PER_KG, 3, RoundingMode.HALF_UP));
    }

    private static String appleWorkoutNote(AppleHealthWorkout workout) {
        StringBuilder note = new StringBuilder("Apple Health");
        if (workout.getSourceName() != null && !workout.getSourceName().isBlank()) {
            note.append(" · ").append(workout.getSourceName());
        }
        if (workout.getDistanceMiles() != null) {
            note.append(" · ").append(workout.getDistanceMiles().stripTrailingZeros().toPlainString()).append(" mi");
        }
        if (workout.getEnergyKcal() != null) {
            note.append(" · ").append(workout.getEnergyKcal().stripTrailingZeros().toPlainString()).append(" kcal");
        }
        return note.toString();
    }

    private void assertRowAccess(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new NotFoundException("Resource not found");
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new NotFoundException("Resource not found");
        }
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
