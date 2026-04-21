package com.svp.tracker.admin.controller;

import com.svp.tracker.management.dto.ManagementDayOneTagDefDto;
import com.svp.tracker.management.service.ManagementDayOneService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/day-one-tags")
@RequiredArgsConstructor
public class AdminDayOneTagController {

    private final ManagementDayOneService dayOneService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDayOneTagDefDto create(@RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        return dayOneService.createTag(name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        dayOneService.deleteTag(id);
    }
}
