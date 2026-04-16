package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.dto.ServerLogsDto;
import com.svp.tracker.diagnostics.LogLevelSampleEmitter;
import com.svp.tracker.logging.LogLineBuffer;
import com.svp.tracker.logging.LogWebDisplayFilter;
import com.svp.tracker.logging.LogsApiPollNoise;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

/** In-memory JVM log tail (not server log files on disk). */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@Slf4j
public class ServerLogsController {

    private final LogLevelSampleEmitter logLevelSampleEmitter;

    /** Only lines with a leading timestamp newer than this many minutes are returned to the Logs UI (0 = no limit). */
    @Value("${tracker.logging.web-display-max-age-minutes:30}")
    private int webDisplayMaxAgeMinutes;

    @GetMapping
    public ServerLogsDto tail(@RequestParam(name = "limit", defaultValue = "800") int limit) {
        int capped = Math.min(Math.max(limit, 1), 2_000);
        var raw = LogLineBuffer.tail(capped);
        var withoutNoise = LogsApiPollNoise.filterLines(raw);
        var lines = LogWebDisplayFilter.keepWithinLastMinutes(withoutNoise, webDisplayMaxAgeMinutes);
        return new ServerLogsDto(lines, capped, lines.size(), LogLineBuffer.bufferedSize());
    }

    /** Writes TRACE/DEBUG/INFO/WARN/ERROR sample lines (honors {@code logging.level} for package diagnostics). */
    @PostMapping("/samples")
    public ResponseEntity<Map<String, String>> emitSamples() {
        log.info("POST /api/admin/logs/samples — emitting multi-level sample lines");
        logLevelSampleEmitter.emitSampleLines();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Emitted sample lines at SLF4J levels"));
    }
}
