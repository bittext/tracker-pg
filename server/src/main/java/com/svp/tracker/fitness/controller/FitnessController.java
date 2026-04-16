package com.svp.tracker.fitness.controller;

import com.svp.tracker.fitness.domain.BodyWeightLog;
import com.svp.tracker.fitness.domain.Exercise;
import com.svp.tracker.fitness.domain.ExerciseDayLog;
import com.svp.tracker.fitness.service.FitnessService;
import com.svp.tracker.fitness.dto.BodyWeightCreateRequest;
import com.svp.tracker.fitness.dto.DailyExerciseReportDto;
import com.svp.tracker.fitness.dto.MonthActivityCalendarDto;
import com.svp.tracker.fitness.dto.MonthlyExerciseReportDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fitness")
@RequiredArgsConstructor
public class FitnessController {

    private final FitnessService fitnessService;

    @GetMapping("/exercises")
    public List<Exercise> listExercises() {
        return fitnessService.listExercises();
    }

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public Exercise createExercise(@Valid @RequestBody Exercise body) {
        return fitnessService.createExercise(body);
    }

    @GetMapping("/exercises/{id}")
    public Exercise getExercise(@PathVariable Long id) {
        return fitnessService.getExercise(id);
    }

    @PutMapping("/exercises/{id}")
    public Exercise updateExercise(@PathVariable Long id, @RequestBody Exercise body) {
        return fitnessService.updateExercise(id, body);
    }

    @DeleteMapping("/exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable Long id) {
        fitnessService.deleteExercise(id);
    }

    @GetMapping("/exercises/{exerciseId}/day-logs")
    public List<ExerciseDayLog> listDayLogsForDay(
            @PathVariable Long exerciseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate day) {
        return fitnessService.listDayLogsForDay(exerciseId, day);
    }

    @GetMapping("/day-logs")
    public List<ExerciseDayLog> listDayLogsBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fitnessService.listDayLogsBetween(from, to);
    }

    @PostMapping("/exercises/{exerciseId}/day-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDayLog addDayLog(@PathVariable Long exerciseId, @Valid @RequestBody ExerciseDayLog body) {
        return fitnessService.addDayLog(exerciseId, body);
    }

    @PutMapping("/day-logs/{id}")
    public ExerciseDayLog updateDayLog(@PathVariable Long id, @RequestBody ExerciseDayLog body) {
        return fitnessService.updateDayLog(id, body);
    }

    @DeleteMapping("/day-logs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDayLog(@PathVariable Long id) {
        fitnessService.deleteDayLog(id);
    }

    @GetMapping("/reports/daily")
    public DailyExerciseReportDto dailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate date) {
        return fitnessService.dailyReport(date);
    }

    @GetMapping("/reports/monthly")
    public MonthlyExerciseReportDto monthlyReport(@RequestParam int year, @RequestParam int month) {
        return fitnessService.monthlyReport(year, month);
    }

    @GetMapping("/reports/month-calendar")
    public MonthActivityCalendarDto monthCalendar(@RequestParam int year, @RequestParam int month) {
        return fitnessService.monthActivityCalendar(year, month);
    }

    @GetMapping("/body-weight")
    public List<BodyWeightLog> listBodyWeight() {
        return fitnessService.listBodyWeight();
    }

    @PostMapping("/body-weight")
    @ResponseStatus(HttpStatus.CREATED)
    public BodyWeightLog logBodyWeight(@Valid @RequestBody BodyWeightCreateRequest body) {
        return fitnessService.logBodyWeight(body.toEntity());
    }

    @PutMapping("/body-weight/{id}")
    public BodyWeightLog updateBodyWeight(@PathVariable Long id, @RequestBody BodyWeightLog body) {
        return fitnessService.updateBodyWeight(id, body);
    }

    @DeleteMapping("/body-weight/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBodyWeight(@PathVariable Long id) {
        fitnessService.deleteBodyWeight(id);
    }
}
