package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.FinanceTaxDeskRhAccountRealizedDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RobinhoodBrokerRealizedPnlServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseSidecarSumsCalendarWindowAndRequiresIndividual() throws Exception {
        var root = mapper.readTree(
                """
                {
                  "start_date": "2026-01-01",
                  "end_date": "2026-09-16",
                  "total_realized": "258299.52",
                  "accounts": [
                    {"suffix": "3370", "label": "Individual", "realized": "258511.27", "closing_trades": 40},
                    {"suffix": "3550", "label": "Agentic", "realized": "-150.00", "closing_trades": 2},
                    {"suffix": "8696", "label": "Ammu", "realized": "-61.75", "closing_trades": 1}
                  ],
                  "warnings": []
                }
                """);
        Optional<RobinhoodBrokerRealizedPnlService.Fetched> parsed =
                RobinhoodBrokerRealizedPnlService.parseSidecar(
                        root, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 16));
        assertTrue(parsed.isPresent());
        assertEquals(0, new BigDecimal("258299.52").compareTo(parsed.get().total()));
        assertEquals(3, parsed.get().accounts().size());
        FinanceTaxDeskRhAccountRealizedDto individual = parsed.get().accounts().get(0);
        assertEquals("3370", individual.suffix());
        assertEquals(0, new BigDecimal("258511.27").compareTo(individual.realized()));
    }

    @Test
    void parseSidecarRejectsWhenIndividualMissing() throws Exception {
        var root = mapper.readTree(
                """
                {"total_realized": "-150.00", "accounts": [
                  {"suffix": "3550", "label": "Agentic", "realized": "-150.00", "closing_trades": 1}
                ]}
                """);
        assertTrue(RobinhoodBrokerRealizedPnlService.parseSidecar(
                        root, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 16))
                .isEmpty());
    }
}
