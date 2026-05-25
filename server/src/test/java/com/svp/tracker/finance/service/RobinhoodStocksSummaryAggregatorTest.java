package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.svp.tracker.finance.dto.RobinhoodStocksSummaryRow;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodStocksSummaryAggregatorTest {

    @Test
    void longOptionRoundTripUsesBtoAndStcColumns() {
        List<Map<String, Object>> rows =
                List.of(
                        row("BTO", "NBIS", "NBIS 5/22/2026 Call $215.00", "2", "-1146.08"),
                        row("STC", "NBIS", "NBIS 5/22/2026 Call $215.00", "2", "4799.73"));

        RobinhoodStocksSummaryRow summary = singleRow(rows);

        assertEquals(new BigDecimal("2"), summary.btoQuantity());
        assertEquals(new BigDecimal("2"), summary.stcQuantity());
        assertEquals(BigDecimal.ZERO, summary.stoQuantity());
        assertEquals(BigDecimal.ZERO, summary.btcQuantity());
        assertEquals(new BigDecimal("2"), summary.totalBuyQuantity());
        assertEquals(new BigDecimal("2"), summary.totalSellQuantity());
    }

    @Test
    void shortOptionRoundTripDoesNotLookLikeBuyTwoSellFour() {
        List<Map<String, Object>> rows =
                List.of(
                        row("STO", "NBIS", "NBIS 5/22/2026 Call $215.00", "4", "4799.73"),
                        row("BTC", "NBIS", "NBIS 5/22/2026 Call $215.00", "2", "-1146.08"));

        RobinhoodStocksSummaryRow summary = singleRow(rows);

        assertEquals(BigDecimal.ZERO, summary.btoQuantity());
        assertEquals(BigDecimal.ZERO, summary.stcQuantity());
        assertEquals(new BigDecimal("4"), summary.stoQuantity());
        assertEquals(new BigDecimal("2"), summary.btcQuantity());
        assertEquals(new BigDecimal("2"), summary.totalBuyQuantity());
        assertEquals(new BigDecimal("4"), summary.totalSellQuantity());
    }

    @Test
    void closeLongCanExceedOpenLongWithinSameYear() {
        List<Map<String, Object>> rows =
                List.of(
                        row("BTO", "NBIS", "NBIS 5/22/2026 Call $215.00", "2", "-1146.08"),
                        row("STC", "NBIS", "NBIS 5/22/2026 Call $215.00", "4", "4799.73"));

        RobinhoodStocksSummaryRow summary = singleRow(rows);

        assertEquals(new BigDecimal("2"), summary.btoQuantity());
        assertEquals(new BigDecimal("4"), summary.stcQuantity());
    }

    private static RobinhoodStocksSummaryRow singleRow(List<Map<String, Object>> rows) {
        List<RobinhoodStocksSummaryRow> out = RobinhoodStocksSummaryAggregator.aggregate(rows, 2026);
        assertEquals(1, out.size());
        return out.get(0);
    }

    private static Map<String, Object> row(
            String trans, String instrument, String description, String quantity, String amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("TRANS_CODE", trans);
        m.put("INSTRUMENT", instrument);
        m.put("DESCRIPTION", description);
        m.put("QUANTITY", quantity);
        m.put("AMOUNT", amount);
        m.put("ACTIVITY_DATE", "2026-05-21");
        return m;
    }
}
