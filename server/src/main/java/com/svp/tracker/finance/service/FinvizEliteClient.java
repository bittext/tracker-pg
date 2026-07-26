package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Low-level Finviz Elite CSV export client. Auth is the Elite {@code auth} query parameter only —
 * never scrape HTML pages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinvizEliteClient {

    private final FinanceProperties props;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public record CsvTable(List<String> columns, List<Map<String, String>> rows) {}

    public CsvTable export(Map<String, String> queryParams) {
        if (!props.finvizEliteConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Finviz Elite is disabled or TRACKER_FINANCE_FINVIZ_ELITE_API_KEY is not set");
        }
        StringBuilder url = new StringBuilder(props.finvizEliteBaseUrl());
        url.append(props.finvizEliteBaseUrl().contains("?") ? '&' : '?');
        url.append("auth=").append(URLEncoder.encode(props.finvizEliteApiKey(), StandardCharsets.UTF_8));
        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || "auth".equalsIgnoreCase(e.getKey())) {
                    continue;
                }
                if (e.getValue() == null || e.getValue().isBlank()) {
                    continue;
                }
                url.append('&')
                        .append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofMillis(props.finvizEliteTimeoutMs()))
                .header("User-Agent", "tracker-pg/finviz-elite")
                .header("Accept", "text/csv,text/plain,*/*")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body();
            if (code == 401 || code == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Finviz Elite auth failed — check API key / subscription");
            }
            if (code < 200 || code >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Finviz Elite export HTTP " + code);
            }
            String lower = body.toLowerCase(Locale.ROOT);
            if (lower.contains("invalid") && lower.contains("auth") && !body.contains("Ticker")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Finviz Elite rejected the API key");
            }
            if (body.isBlank()) {
                return new CsvTable(List.of(), List.of());
            }
            return parseCsv(body);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Finviz Elite network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Finviz Elite request interrupted");
        }
    }

    static CsvTable parseCsv(String csv) {
        List<String> lines = splitLines(csv);
        if (lines.isEmpty()) {
            return new CsvTable(List.of(), List.of());
        }
        List<String> columns = parseRow(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = parseRow(lines.get(i));
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                String key = columns.get(c);
                if (key == null || key.isBlank()) {
                    key = "col" + c;
                }
                row.put(key, c < cells.size() ? cells.get(c) : "");
            }
            rows.add(row);
        }
        return new CsvTable(List.copyOf(columns), List.copyOf(rows));
    }

    private static List<String> splitLines(String csv) {
        String[] raw = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            if (line != null && !line.isBlank()) {
                out.add(line);
            }
        }
        return out;
    }

    /** Minimal RFC4180-ish CSV row parser (quotes + commas). */
    static List<String> parseRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells;
    }
}
