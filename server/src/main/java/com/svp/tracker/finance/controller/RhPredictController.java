package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RhPredictCloseDto;
import com.svp.tracker.finance.dto.RhPredictCloseLabelRequest;
import com.svp.tracker.finance.dto.RhPredictDeskDto;
import com.svp.tracker.finance.service.RhPredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markets/predict")
@RequiredArgsConstructor
@Slf4j
public class RhPredictController {

    private final RhPredictService predictService;

    @GetMapping
    public RhPredictDeskDto load() {
        return predictService.loadForCurrentUser();
    }

    @PostMapping("/refresh")
    public RhPredictDeskDto refresh() {
        log.info("POST /api/markets/predict/refresh");
        return predictService.refreshForCurrentUser();
    }

    @PutMapping("/closes/{id}")
    public RhPredictCloseDto rename(@PathVariable long id, @RequestBody RhPredictCloseLabelRequest body) {
        return predictService.renameClose(id, body);
    }
}
