package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.Btc15DeskDto;
import com.svp.tracker.finance.dto.RhPredictCloseDto;
import com.svp.tracker.finance.dto.RhPredictCloseLabelRequest;
import com.svp.tracker.finance.dto.RhPredictDeskDto;
import com.svp.tracker.finance.dto.RhPredictOpenCostRequest;
import com.svp.tracker.finance.service.Btc15Service;
import com.svp.tracker.finance.service.RhPredictService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markets/predict")
@RequiredArgsConstructor
@Slf4j
public class RhPredictController {

    private final RhPredictService predictService;
    private final Btc15Service btc15Service;

    @GetMapping
    public RhPredictDeskDto load() {
        return predictService.loadForCurrentUser();
    }

    @GetMapping("/btc-15")
    public Btc15DeskDto btc15(@RequestParam(name = "date", required = false) LocalDate date) {
        return btc15Service.load(date);
    }

    @PostMapping("/refresh")
    public RhPredictDeskDto refresh() {
        log.info("POST /api/markets/predict/refresh");
        return predictService.refreshForCurrentUser();
    }

    @PutMapping("/open-cost")
    public RhPredictDeskDto setOpenCost(@RequestBody RhPredictOpenCostRequest body) {
        return predictService.setOpenCost(body == null ? null : body.amount());
    }

    @PutMapping("/closes/{id}")
    public RhPredictCloseDto rename(@PathVariable long id, @RequestBody RhPredictCloseLabelRequest body) {
        return predictService.renameClose(id, body);
    }
}
