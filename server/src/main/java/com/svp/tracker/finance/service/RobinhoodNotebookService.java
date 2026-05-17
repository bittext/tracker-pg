package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodNotebookBundleDto;
import com.svp.tracker.finance.dto.RobinhoodNotebookConfigDto;
import com.svp.tracker.finance.dto.RobinhoodNotebookRenderDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceReportDto;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodNotebookService {

    private static final Set<String> NOTEBOOK_IDS = Set.of("performance", "risk");

    private final RobinhoodFinanceService robinhoodFinanceService;
    private final RobinhoodPerformanceReportService robinhoodPerformanceReportService;
    private final FinanceProperties financeProperties;

    public RobinhoodNotebookConfigDto notebookConfig() {
        String jupyterUrl = financeProperties.robinhoodJupyterLabUrl().trim();
        String svcUrl = financeProperties.robinhoodNotebookServiceBaseUrl().trim();
        boolean jupyter = !jupyterUrl.isBlank();
        boolean svc = financeProperties.robinhoodNotebookServiceEnabled() && !svcUrl.isBlank();
        String svcNote =
                svc
                        ? "Server can render parameterized notebooks via robinhood-notebook-svc."
                        : "Set tracker.finance.robinhood-notebook-service-enabled and base-url to enable server-side notebook HTML.";
        return new RobinhoodNotebookConfigDto(jupyter, jupyter ? jupyterUrl : "", svc, svcNote);
    }

    public RobinhoodNotebookBundleDto buildBundle(int year, String symbolFilter) {
        int cap = financeProperties.maxStocksSummaryRows();
        List<Map<String, Object>> rows = robinhoodFinanceService.loadYearTransactionRows(year, symbolFilter);
        boolean truncated = rows.size() >= cap;
        RobinhoodPerformanceReportDto report = robinhoodPerformanceReportService.buildReport(year, symbolFilter);
        var closedTrades = robinhoodPerformanceReportService.listClosedTrades(year, symbolFilter);
        String note =
                "Import this JSON in Jupyter (notebooks/robinhood). Transactions are user-scoped; performance uses FIFO on trade legs. Not tax advice.";
        if (truncated) {
            note += " Transactions list capped at " + cap + " rows.";
        }
        return new RobinhoodNotebookBundleDto(
                year,
                symbolFilter != null && !symbolFilter.isBlank() ? symbolFilter.trim() : null,
                Instant.now(),
                rows.size(),
                truncated,
                rows,
                report,
                closedTrades,
                note);
    }

    public RobinhoodNotebookRenderDto renderNotebookHtml(int year, String symbolFilter, String notebookId) {
        if (!financeProperties.robinhoodNotebookServiceEnabled()) {
            return new RobinhoodNotebookRenderDto(
                    year,
                    "",
                    "disabled",
                    "Notebook rendering is disabled. Enable tracker.finance.robinhood-notebook-service-enabled.");
        }
        String base = financeProperties.robinhoodNotebookServiceBaseUrl().trim();
        if (base.isBlank()) {
            return new RobinhoodNotebookRenderDto(
                    year, "", "disabled", "Configure tracker.finance.robinhood-notebook-service-base-url.");
        }
        String notebook = normalizeNotebookId(notebookId);
        RobinhoodNotebookBundleDto bundle = buildBundle(year, symbolFilter);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notebook", notebook);
        payload.put("year", bundle.year());
        payload.put("filterInstrument", bundle.filterInstrument());
        payload.put("exportedAt", bundle.exportedAt());
        payload.put("transactionRowCount", bundle.transactionRowCount());
        payload.put("transactionsTruncated", bundle.transactionsTruncated());
        payload.put("transactions", bundle.transactions());
        payload.put("performanceReport", bundle.performanceReport());
        payload.put("closedTrades", bundle.closedTrades());
        payload.put("usageNote", bundle.usageNote());
        try {
            String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            RestClient client = RestClient.builder().baseUrl(normalized).build();
            RenderResponse body =
                    client.post()
                            .uri("/v1/render")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
                            .retrieve()
                            .body(RenderResponse.class);
            if (body == null || body.html() == null) {
                return new RobinhoodNotebookRenderDto(year, "", "error", "Empty response from notebook service.");
            }
            return new RobinhoodNotebookRenderDto(
                    year, body.html(), body.source() != null ? body.source() : "papermill", body.note());
        } catch (Exception e) {
            log.warn("Robinhood notebook render failed: {}", e.getMessage());
            return new RobinhoodNotebookRenderDto(
                    year, "", "error", "Notebook service unavailable: " + e.getMessage());
        }
    }

    static String normalizeNotebookId(String notebookId) {
        if (notebookId == null || notebookId.isBlank()) {
            return "performance";
        }
        String id = notebookId.trim().toLowerCase(Locale.ROOT);
        if (!NOTEBOOK_IDS.contains(id)) {
            throw new IllegalArgumentException("Unknown notebook: " + notebookId + " (use performance or risk)");
        }
        return id;
    }

    private record RenderResponse(String html, String source, String note) {}
}
