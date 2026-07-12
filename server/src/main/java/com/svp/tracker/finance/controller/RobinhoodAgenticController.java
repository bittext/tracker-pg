package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeEvaluateDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderRequestDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrdersDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticPositionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncedOrdersDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.service.RobinhoodAgenticAutoTradeService;
import com.svp.tracker.finance.service.RobinhoodAgenticOrderService;
import com.svp.tracker.finance.service.RobinhoodAgenticService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/robinhood/agentic", "/api/markets/agentic"})
@RequiredArgsConstructor
public class RobinhoodAgenticController {

    private final RobinhoodAgenticService agenticService;
    private final RobinhoodAgenticOrderService orderService;
    private final RobinhoodAgenticAutoTradeService autoTradeService;

    @GetMapping("/status")
    public RobinhoodAgenticStatusDto status() {
        return agenticService.status();
    }

    @PostMapping("/tokens")
    public RobinhoodAgenticStatusDto saveTokens(@RequestBody RobinhoodAgenticTokensRequestDto body) {
        return agenticService.saveTokens(body);
    }

    @DeleteMapping("/connection")
    public void disconnect() {
        agenticService.disconnect();
    }

    @PostMapping("/sync")
    public RobinhoodAgenticSyncResultDto sync() {
        return agenticService.syncNow();
    }

    @GetMapping("/positions")
    public RobinhoodAgenticPositionsDto positions() {
        return agenticService.positions();
    }

    @GetMapping("/synced-orders")
    public RobinhoodAgenticSyncedOrdersDto syncedOrders() {
        return agenticService.syncedOrders();
    }

    @GetMapping("/settings")
    public RobinhoodAgenticSettingsDto settings() {
        return orderService.settings();
    }

    @PutMapping("/settings")
    public RobinhoodAgenticSettingsDto saveSettings(@RequestBody RobinhoodAgenticSettingsRequestDto body) {
        return orderService.saveSettings(body);
    }

    @GetMapping("/orders")
    public RobinhoodAgenticOrdersDto orders() {
        return orderService.orders();
    }

    @PostMapping("/orders/review")
    public RobinhoodAgenticOrderDto reviewOrder(@RequestBody RobinhoodAgenticOrderRequestDto body) {
        return orderService.reviewOrder(body);
    }

    @PostMapping("/orders/{id}/approve")
    public RobinhoodAgenticOrderDto approveOrder(@PathVariable long id) {
        return orderService.approveOrder(id);
    }

    @PostMapping("/orders/{id}/reject")
    public RobinhoodAgenticOrderDto rejectOrder(@PathVariable long id) {
        return orderService.rejectOrder(id);
    }

    @PostMapping("/auto-trade/evaluate")
    public RobinhoodAgenticAutoTradeEvaluateDto evaluateAutoTrade() {
        return autoTradeService.evaluateForCurrentUser();
    }

    @GetMapping("/auto-trade/runs")
    public List<RobinhoodAgenticAutoTradeRunDto> autoTradeRuns() {
        return autoTradeService.recentRuns();
    }
}
