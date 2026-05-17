package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodPortfolioOverviewDto;
import com.svp.tracker.finance.dto.RobinhoodPortfolioPositionDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Loads optional Robinhood portfolio snapshot JSON (source of truth from the mobile/web app). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodPortfolioSnapshotService {

    private static final String CLASSPATH_SNAPSHOT = "robinhood-portfolio-snapshot.json";

    private final FinanceProperties financeProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<RobinhoodPortfolioOverviewDto> loadSnapshot() {
        JsonNode root = readRoot();
        if (root == null || root.isMissingNode()) {
            return Optional.empty();
        }
        try {
            LocalDate asOf = LocalDate.parse(root.path("asOf").asText());
            List<RobinhoodPortfolioPositionDto> positions = new ArrayList<>();
            for (JsonNode p : root.path("positions")) {
                positions.add(parsePosition(p));
            }
            BigDecimal openUnrealized = decimal(root, "openUnrealizedPnL");
            if (openUnrealized == null) {
                openUnrealized =
                        positions.stream()
                                .map(RobinhoodPortfolioPositionDto::openPnL)
                                .filter(v -> v != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            String note = root.path("note").asText("");
            if (note.isBlank()) {
                note = "Portfolio figures from configured Robinhood snapshot file.";
            }
            return Optional.of(
                    new RobinhoodPortfolioOverviewDto(
                            asOf,
                            "snapshot",
                            decimal(root, "portfolioValue"),
                            decimal(root, "cash"),
                            decimal(root, "todayPnL"),
                            root.has("todayPnLPercent") && !root.path("todayPnLPercent").isNull()
                                    ? root.path("todayPnLPercent").asDouble()
                                    : null,
                            decimal(root, "ytdTotalPnL"),
                            decimal(root, "todayRealizedPnL"),
                            decimal(root, "ytdRealizedPnL"),
                            openUnrealized.setScale(2, RoundingMode.HALF_UP),
                            positions,
                            note));
        } catch (Exception e) {
            log.warn("Robinhood portfolio snapshot parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode readRoot() {
        String configured = financeProperties.robinhoodPortfolioSnapshotPath().trim();
        if (!configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path)) {
                try {
                    return objectMapper.readTree(Files.readString(path));
                } catch (IOException e) {
                    log.warn("Could not read Robinhood snapshot at {}: {}", path, e.getMessage());
                }
            } else {
                log.warn("Robinhood portfolio snapshot path not found: {}", path);
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_SNAPSHOT);
            if (resource.exists()) {
                return objectMapper.readTree(resource.getInputStream());
            }
        } catch (IOException e) {
            log.warn("Could not read classpath Robinhood snapshot: {}", e.getMessage());
        }
        Path repoDefault = Path.of("config", "robinhood-portfolio-snapshot.json");
        if (Files.isRegularFile(repoDefault)) {
            try {
                return objectMapper.readTree(Files.readString(repoDefault));
            } catch (IOException e) {
                log.warn("Could not read {}: {}", repoDefault, e.getMessage());
            }
        }
        return null;
    }

    private static RobinhoodPortfolioPositionDto parsePosition(JsonNode p) {
        String instrument = p.path("instrument").asText("").trim();
        String contract = p.path("contract").asText("").trim();
        if (contract.isEmpty() && p.has("name")) {
            String name = p.path("name").asText("").trim();
            if (!name.equalsIgnoreCase(instrument)) {
                contract = name;
            }
        }
        return new RobinhoodPortfolioPositionDto(
                instrument,
                p.path("name").asText(instrument),
                contract.isEmpty() ? null : contract,
                p.path("assetClass").asText("Equity"),
                decimal(p, "quantity"),
                decimal(p, "avgPrice"),
                decimal(p, "marketPrice"),
                decimal(p, "marketValue"),
                decimal(p, "openPnL"),
                decimal(p, "dayOpenPnL"),
                p.has("dayOpenPnLPercent") && !p.path("dayOpenPnLPercent").isNull()
                        ? p.path("dayOpenPnLPercent").asDouble()
                        : null);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return BigDecimal.valueOf(v.asDouble()).setScale(4, RoundingMode.HALF_UP);
        }
        String s = v.asText("").trim();
        if (s.isEmpty()) {
            return null;
        }
        return new BigDecimal(s.replace(",", "")).setScale(4, RoundingMode.HALF_UP);
    }
}
