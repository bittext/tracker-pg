package com.svp.tracker.journal.controller;

import com.svp.tracker.journal.dto.JournalCourseDto;
import com.svp.tracker.journal.dto.JournalCourseWriteRequest;
import com.svp.tracker.journal.service.JournalCourseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
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
@RequestMapping("/api/journal/courses")
@RequiredArgsConstructor
public class JournalCourseController {

    private final JournalCourseService service;

    @GetMapping
    public List<JournalCourseDto> list(
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable String q) {
        return service.list(status, q);
    }

    @GetMapping("/{id}")
    public JournalCourseDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JournalCourseDto create(@Valid @RequestBody JournalCourseWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public JournalCourseDto update(@PathVariable long id, @Valid @RequestBody JournalCourseWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
