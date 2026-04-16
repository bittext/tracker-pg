package com.svp.tracker.management.service;

import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.BalanceUrgency;
import com.svp.tracker.management.domain.ManagementTask;
import com.svp.tracker.management.domain.ManagementTaskCategory;
import com.svp.tracker.management.domain.ManagementTaskType;
import com.svp.tracker.management.dto.ManagementTaskDto;
import com.svp.tracker.management.dto.ManagementTaskWriteRequest;
import com.svp.tracker.management.dto.TaskMonthCalendarDto;
import com.svp.tracker.management.repository.ManagementTaskCategoryRepository;
import com.svp.tracker.management.repository.ManagementTaskRepository;
import com.svp.tracker.management.repository.ManagementTaskTypeRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementService {

    private final ManagementTaskCategoryRepository categoryRepository;
    private final ManagementTaskTypeRepository taskTypeRepository;
    private final ManagementTaskRepository taskRepository;

    public List<ManagementTaskCategory> listCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(ManagementTaskCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public ManagementTaskCategory createCategory(ManagementTaskCategory body) {
        body.setId(null);
        return categoryRepository.save(body);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new NotFoundException("Category not found: " + id);
        }
        taskRepository.clearCategoryByCategoryId(id);
        categoryRepository.deleteById(id);
    }

    public List<ManagementTaskType> listTaskTypes() {
        return taskTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(ManagementTaskType::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public ManagementTaskType createTaskType(ManagementTaskType body) {
        body.setId(null);
        return taskTypeRepository.save(body);
    }

    @Transactional
    public void deleteTaskType(Long id) {
        if (!taskTypeRepository.existsById(id)) {
            throw new NotFoundException("Task type not found: " + id);
        }
        taskRepository.clearTaskTypeByTypeId(id);
        taskTypeRepository.deleteById(id);
    }

    public List<ManagementTaskDto> listTasksForReport() {
        return taskRepository.findAll().stream()
                .sorted(taskReportOrder())
                .map(this::toDto)
                .toList();
    }

    public List<ManagementTaskDto> listTasksUnscheduled() {
        return taskRepository.findByDueDateIsNullOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public TaskMonthCalendarDto taskCalendar(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ManagementTask> inMonth = taskRepository.findByDueDateBetweenOrderByDueDateAscIdAsc(from, to);
        Map<String, List<ManagementTaskDto>> byDay = new HashMap<>();
        for (ManagementTask t : inMonth) {
            if (t.getDueDate() == null) {
                continue;
            }
            String key = t.getDueDate().toString();
            byDay.computeIfAbsent(key, k -> new ArrayList<>()).add(toDto(t));
        }
        for (Map.Entry<String, List<ManagementTaskDto>> e : byDay.entrySet()) {
            e.getValue().sort(Comparator.comparing(ManagementTaskDto::getUrgency).reversed());
        }
        return new TaskMonthCalendarDto(year, month, byDay);
    }

    @Transactional
    public ManagementTaskDto createTask(ManagementTaskWriteRequest req) {
        ManagementTask entity = new ManagementTask();
        applyWriteRequest(entity, req, true);
        return toDto(taskRepository.save(entity));
    }

    public ManagementTaskDto getTask(Long id) {
        return toDto(taskRepository.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id)));
    }

    @Transactional
    public ManagementTaskDto updateTask(Long id, ManagementTaskWriteRequest req) {
        ManagementTask entity =
                taskRepository.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id));
        applyWriteRequest(entity, req, false);
        return toDto(taskRepository.save(entity));
    }

    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new NotFoundException("Task not found: " + id);
        }
        taskRepository.deleteById(id);
    }

    private void applyWriteRequest(ManagementTask entity, ManagementTaskWriteRequest req, boolean isCreate) {
        entity.setTitle(req.getTitle().trim());
        entity.setNotes(req.getNotes() != null ? req.getNotes() : "");
        entity.setDueDate(req.getDueDate());
        if (req.getUrgency() != null) {
            entity.setUrgency(req.getUrgency());
        } else if (isCreate) {
            entity.setUrgency(BalanceUrgency.MEDIUM);
        }
        if (req.getCompleted() != null) {
            entity.setCompleted(req.getCompleted());
        } else if (isCreate) {
            entity.setCompleted(false);
        }
        if (req.getCategoryId() != null) {
            entity.setCategory(categoryRepository
                    .findById(req.getCategoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found: " + req.getCategoryId())));
        } else {
            entity.setCategory(null);
        }
        if (req.getTaskTypeId() != null) {
            entity.setTaskType(taskTypeRepository
                    .findById(req.getTaskTypeId())
                    .orElseThrow(() -> new NotFoundException("Task type not found: " + req.getTaskTypeId())));
        } else {
            entity.setTaskType(null);
        }
    }

    private Comparator<ManagementTask> taskReportOrder() {
        return Comparator.comparing(ManagementTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ManagementTask::getUrgency, Comparator.reverseOrder())
                .thenComparing(ManagementTask::getId);
    }

    private ManagementTaskDto toDto(ManagementTask t) {
        return ManagementTaskDto.builder()
                .id(t.getId())
                .title(t.getTitle())
                .notes(t.getNotes())
                .dueDate(t.getDueDate())
                .urgency(t.getUrgency())
                .completed(t.isCompleted())
                .categoryId(t.getCategory() != null ? t.getCategory().getId() : null)
                .categoryName(t.getCategory() != null ? t.getCategory().getName() : null)
                .taskTypeId(t.getTaskType() != null ? t.getTaskType().getId() : null)
                .taskTypeName(t.getTaskType() != null ? t.getTaskType().getName() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
