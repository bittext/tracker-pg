package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RobinhoodRhCashFlowAllocatorTest {

    @Test
    void mirrorsInternalOutFromIndividualToAgentic() {
        RobinhoodRhCashFlowEventDto out = new RobinhoodRhCashFlowEventDto(
                LocalDate.of(2026, 4, 10),
                "OUT",
                new BigDecimal("1000.00"),
                "ITRF",
                "Internal transfer to Agentic",
                "CSV",
                "INTERNAL_OUT",
                true,
                null);

        Map<String, List<RobinhoodRhCashFlowEventDto>> bySuffix =
                RobinhoodRhCashFlowAllocator.allocateByAccountSuffix(
                        List.of(out), "3370", "3550", "4123", Set.of("3370", "3550", "4123"));

        assertEquals(1, bySuffix.get("3370").size());
        assertEquals("OUT", bySuffix.get("3370").get(0).direction());
        assertEquals(1, bySuffix.get("3550").size());
        assertEquals("IN", bySuffix.get("3550").get(0).direction());
        assertEquals(new BigDecimal("1000.00"), bySuffix.get("3550").get(0).amount());
        assertEquals("INTERNAL_IN", bySuffix.get("3550").get(0).flowCategory());
        assertTrue(bySuffix.get("3550").get(0).internalTransfer());
    }

    @Test
    void brokerageToBrokerageItrfOnJun12MirrorsToAgentic() {
        RobinhoodRhCashFlowEventDto out = new RobinhoodRhCashFlowEventDto(
                LocalDate.of(2026, 6, 12),
                "OUT",
                new BigDecimal("1000.00"),
                "ITRF",
                "Transfer from Brokerage to Brokerage",
                "CSV",
                "INTERNAL_OUT",
                true,
                null);

        Map<String, List<RobinhoodRhCashFlowEventDto>> bySuffix =
                RobinhoodRhCashFlowAllocator.allocateByAccountSuffix(
                        List.of(out), "3370", "3550", "4123", Set.of("3370", "3550", "4123"));

        assertEquals(1, bySuffix.get("3550").size());
        assertEquals("IN", bySuffix.get("3550").get(0).direction());
        assertEquals(new BigDecimal("1000.00"), bySuffix.get("3550").get(0).amount());
        assertTrue(bySuffix.get("4123").isEmpty());
    }

    @Test
    void brokerageToBrokerageItrfMirrorsToManaged() {
        RobinhoodRhCashFlowEventDto out = new RobinhoodRhCashFlowEventDto(
                LocalDate.of(2026, 6, 24),
                "OUT",
                new BigDecimal("400.00"),
                "ITRF",
                "Transfer from Brokerage to Brokerage",
                "CSV",
                "INTERNAL_OUT",
                true,
                null);

        Map<String, List<RobinhoodRhCashFlowEventDto>> bySuffix =
                RobinhoodRhCashFlowAllocator.allocateByAccountSuffix(
                        List.of(out), "3370", "3550", "4123", Set.of("3370", "3550", "4123"));

        assertEquals(1, bySuffix.get("3370").size());
        assertEquals("OUT", bySuffix.get("3370").get(0).direction());
        assertEquals("INTERNAL_OUT", bySuffix.get("3370").get(0).flowCategory());
        assertEquals(1, bySuffix.get("4123").size());
        assertEquals("IN", bySuffix.get("4123").get(0).direction());
        assertEquals(new BigDecimal("400.00"), bySuffix.get("4123").get(0).amount());
        assertEquals("INTERNAL_IN", bySuffix.get("4123").get(0).flowCategory());
        assertTrue(bySuffix.get("3550").isEmpty());
    }

    @Test
    void itrfWithoutDescriptionDefaultsToAgentic() {
        RobinhoodRhCashFlowEventDto out = new RobinhoodRhCashFlowEventDto(
                LocalDate.of(2026, 4, 10),
                "OUT",
                new BigDecimal("1000.00"),
                "ITRF",
                null,
                "CSV",
                "INTERNAL_OUT",
                true,
                null);

        Map<String, List<RobinhoodRhCashFlowEventDto>> bySuffix =
                RobinhoodRhCashFlowAllocator.allocateByAccountSuffix(
                        List.of(out), "3370", "3550", "4123", Set.of("3370", "3550", "4123"));

        assertEquals(1, bySuffix.get("3550").size());
        assertEquals(new BigDecimal("1000.00"), bySuffix.get("3550").get(0).amount());
    }
}
