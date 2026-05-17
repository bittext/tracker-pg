package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodNotebookBundleDto;
import com.svp.tracker.finance.dto.RobinhoodNotebookConfigDto;
import com.svp.tracker.finance.dto.RobinhoodNotebookRenderDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceReportDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodNotebookService {

    private static final Set<String> NOTEBOOK_IDS = Set.of("performance", "risk");

    /** Spring Boot 4 does not expose an {@link ObjectMapper} bean; local mapper for notebook sidecar JSON only. */
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bundleBody = JSON.convertValue(bundle, new TypeReference<Map<String, Object>>() {});
            String jsonBody = JSON.writeValueAsString(bundleBody);
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            if (bodyBytes.length == 0) {
                return new RobinhoodNotebookRenderDto(year, "", "error", "Notebook bundle serialized to empty body.");
            }
            String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            int timeoutMs = financeProperties.robinhoodNotebookServiceTimeoutMs();
            HttpClient httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 30_000)))
                            .build();
            URI renderUri = URI.create(normalized + "/v1/render/" + notebook);
            HttpRequest request =
                    HttpRequest.newBuilder(renderUri)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .header("Content-Type", "application/json; charset=utf-8")
                            .header("Accept", "application/json")
                            .header("Content-Length", String.valueOf(bodyBytes.length))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                            .build();
            log.debug("notebook render POST {} bytes to {}", bodyBytes.length, renderUri);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "status " + response.statusCode() + ": " + abbreviate(response.body(), 500));
            }
            RenderResponse body = JSON.readValue(response.body(), RenderResponse.class);
            if (body == null || body.html() == null) {
                return new RobinhoodNotebookRenderDto(year, "", "error", "Empty response from notebook service.");
            }
            return new RobinhoodNotebookRenderDto(
                    year, body.html(), body.source() != null ? body.source() : "papermill", body.note());
        } catch (JsonProcessingException e) {
            log.warn("Robinhood notebook render payload serialization failed: {}", e.getMessage());
            return new RobinhoodNotebookRenderDto(
                    year, "", "error", "Could not serialize notebook bundle: " + e.getMessage());
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

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private record RenderResponse(String html, String source, String note) {}
}
