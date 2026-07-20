package com.svp.tracker.journal.controller;

import com.svp.tracker.journal.dto.JournalBookDto;
import com.svp.tracker.journal.dto.JournalBookWriteRequest;
import com.svp.tracker.journal.service.JournalBookService;
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
@RequestMapping("/api/journal/books")
@RequiredArgsConstructor
public class JournalBookController {

    private final JournalBookService service;

    @GetMapping
    public List<JournalBookDto> list(
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable String q) {
        return service.list(status, q);
    }

    @GetMapping("/{id}")
    public JournalBookDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JournalBookDto create(@Valid @RequestBody JournalBookWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public JournalBookDto update(@PathVariable long id, @Valid @RequestBody JournalBookWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
