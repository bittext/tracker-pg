package com.svp.tracker.finance.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class FinanceTaxDeskWorkbookDtoTest {

    @Test
    void missingBrokerFieldsDefaultToFifoTape() {
        JsonMapper mapper = JsonMapper.builder().build();
        FinanceTaxDeskWorkbookDto original = sample("ROBINHOOD");
        ObjectNode node = (ObjectNode) mapper.readTree(mapper.writeValueAsString(original));
        node.remove("realizedYtdSource");
        node.remove("fifoTapeRealizedYtd");
        node.remove("robinhoodRealizedAccounts");
        FinanceTaxDeskWorkbookDto read = mapper.readValue(node.toString(), FinanceTaxDeskWorkbookDto.class);
        assertEquals("FIFO_TAPE", read.realizedYtdSource());
        assertEquals(0, original.realizedYtd().compareTo(read.fifoTapeRealizedYtd()));
        assertTrue(read.robinhoodRealizedAccounts().isEmpty());
    }

    private static FinanceTaxDeskWorkbookDto sample(String source) {
        BigDecimal z = BigDecimal.ZERO.setScale(2);
        return new FinanceTaxDeskWorkbookDto(
                2026,
                LocalDate.of(2026, 9, 16),
                "MARRIED_FILING_JOINTLY",
                "TX",
                "REFUND_TRACK",
                "ok",
                z,
                z,
                z,
                z,
                z,
                z,
                z,
                z,
                z,
                z,
                "rap",
                z,
                z,
                z,
                true,
                true,
                z,
                z,
                new BigDecimal("-21874.75"),
                z,
                z,
                z,
                z,
                z,
                z,
                z,
                new FinanceTaxDeskSettingsDto(
                        1L, 2026, "MARRIED_FILING_JOINTLY", "TX", z, z, z, z, z, z, ""),
                List.of(),
                List.of(),
                List.of(),
                new FinanceTaxDeskTodayDto(LocalDate.of(2026, 9, 16), 0, 0, z, z, z, List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "narrative",
                source,
                new BigDecimal("-21874.75"),
                List.of(new FinanceTaxDeskRhAccountRealizedDto(
                        "3370", "Individual", new BigDecimal("258511.27"), 12)));
    }
}
