package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementDueCategoryDto;
import com.svp.tracker.management.dto.ManagementDueCategoryWriteRequest;
import com.svp.tracker.management.dto.ManagementDueItemWriteRequest;
import com.svp.tracker.management.dto.ManagementDueMonthDto;
import com.svp.tracker.management.dto.ManagementDueReportDto;
import com.svp.tracker.management.dto.ManagementDueSettleRequest;
import com.svp.tracker.management.service.ManagementDueCategoryService;
import com.svp.tracker.management.service.ManagementDueService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/management/due")
@RequiredArgsConstructor
public class ManagementDueController {

    private final ManagementDueService service;
    private final ManagementDueCategoryService categories;

    @GetMapping("/categories")
    public List<ManagementDueCategoryDto> listCategories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDueCategoryDto createCategory(@Valid @RequestBody ManagementDueCategoryWriteRequest body) {
        return categories.create(body);
    }

    @PutMapping("/categories/{id}")
    public ManagementDueCategoryDto renameCategory(
            @PathVariable long id, @Valid @RequestBody ManagementDueCategoryWriteRequest body) {
        return categories.rename(id, body);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long id) {
        categories.delete(id);
    }

    @PostMapping("/categories/{id}/move")
    public List<ManagementDueCategoryDto> moveCategory(@PathVariable long id, @RequestParam String direction) {
        if (!"up".equalsIgnoreCase(direction) && !"down".equalsIgnoreCase(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be up or down");
        }
        return categories.move(id, "up".equalsIgnoreCase(direction));
    }

    @GetMapping("/month")
    public ManagementDueMonthDto month(@RequestParam int year, @RequestParam int month) {
        return service.month(year, month);
    }

    @GetMapping("/reports")
    public ManagementDueReportDto reports(@RequestParam int year, @RequestParam int month) {
        return service.reports(year, month);
    }

    @PostMapping("/clear-later")
    public ManagementDueMonthDto clearLater(@RequestParam int year, @RequestParam int month) {
        return service.clearLaterDates(year, month);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDueMonthDto create(@Valid @RequestBody ManagementDueItemWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/items/{id}")
    public ManagementDueMonthDto update(@PathVariable long id, @Valid @RequestBody ManagementDueItemWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/items/{id}")
    public ManagementDueMonthDto delete(
            @PathVariable long id, @RequestParam int year, @RequestParam int month) {
        return service.delete(id, year, month);
    }

    @PutMapping("/items/{id}/settle")
    public ManagementDueMonthDto settle(@PathVariable long id, @Valid @RequestBody ManagementDueSettleRequest body) {
        return service.settle(id, body);
    }
}
