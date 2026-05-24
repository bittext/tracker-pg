package com.svp.tracker.management.controller;

import com.svp.tracker.management.domain.ManagementCalendarType;
import com.svp.tracker.management.domain.ManagementNowCardType;
import com.svp.tracker.management.domain.ManagementTaskCategory;
import com.svp.tracker.management.domain.ManagementTaskType;
import com.svp.tracker.management.dto.ManagementCalendarTypeWriteRequest;
import com.svp.tracker.management.dto.ManagementNowCardTypeWriteRequest;
import com.svp.tracker.management.dto.ManagementTaskDto;
import com.svp.tracker.management.dto.ManagementTaskWriteRequest;
import com.svp.tracker.management.dto.TaskMonthCalendarDto;
import com.svp.tracker.management.service.ManagementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class ManagementController {

    private final ManagementService managementService;

    @GetMapping("/categories")
    public List<ManagementTaskCategory> listCategories() {
        return managementService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementTaskCategory createCategory(@Valid @RequestBody ManagementTaskCategory body) {
        return managementService.createCategory(body);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        managementService.deleteCategory(id);
    }

    @GetMapping("/task-types")
    public List<ManagementTaskType> listTaskTypes() {
        return managementService.listTaskTypes();
    }

    @PostMapping("/task-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementTaskType createTaskType(@Valid @RequestBody ManagementTaskType body) {
        return managementService.createTaskType(body);
    }

    @DeleteMapping("/task-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTaskType(@PathVariable Long id) {
        managementService.deleteTaskType(id);
    }

    @GetMapping("/now-card-types")
    public List<ManagementNowCardType> listNowCardTypes() {
        return managementService.listNowCardTypes();
    }

    @PostMapping("/now-card-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementNowCardType createNowCardType(@Valid @RequestBody ManagementNowCardTypeWriteRequest body) {
        return managementService.createNowCardType(body);
    }

    @DeleteMapping("/now-card-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNowCardType(@PathVariable Long id) {
        managementService.deleteNowCardType(id);
    }

    @GetMapping("/calendar-types")
    public List<ManagementCalendarType> listCalendarTypes() {
        return managementService.listCalendarTypes();
    }

    @PostMapping("/calendar-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementCalendarType createCalendarType(@Valid @RequestBody ManagementCalendarTypeWriteRequest body) {
        return managementService.createCalendarType(body);
    }

    @DeleteMapping("/calendar-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCalendarType(@PathVariable Long id) {
        managementService.deleteCalendarType(id);
    }

    @GetMapping("/tasks/unscheduled")
    public List<ManagementTaskDto> listUnscheduledTasks() {
        return managementService.listTasksUnscheduled();
    }

    @GetMapping("/tasks/calendar")
    public TaskMonthCalendarDto taskCalendar(@RequestParam int year, @RequestParam int month) {
        return managementService.taskCalendar(year, month);
    }

    @GetMapping("/tasks")
    public List<ManagementTaskDto> listTasksForReport() {
        return managementService.listTasksForReport();
    }

    @GetMapping("/tasks/{id}")
    public ManagementTaskDto getTask(@PathVariable Long id) {
        return managementService.getTask(id);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementTaskDto createTask(@Valid @RequestBody ManagementTaskWriteRequest body) {
        return managementService.createTask(body);
    }

    @PutMapping("/tasks/{id}")
    public ManagementTaskDto updateTask(@PathVariable Long id, @Valid @RequestBody ManagementTaskWriteRequest body) {
        return managementService.updateTask(id, body);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        managementService.deleteTask(id);
    }

    /** Detailed task listing for Reports → Management (same rows as GET /tasks). */
    @GetMapping("/reports/tasks")
    public List<ManagementTaskDto> tasksReport() {
        return managementService.listTasksForReport();
    }

}
