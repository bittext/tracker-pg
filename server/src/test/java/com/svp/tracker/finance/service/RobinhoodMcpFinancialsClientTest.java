package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobinhoodMcpFinancialsClientTest {

    @Test
    void parseRowsReadsHoodQ2FromStructuredContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.set(
                "structuredContent",
                mapper.readTree(
                        """
                        {"data":{"results":[{"symbol":"HOOD","period":"quarterly","financials":[{\
                        "fiscal_year":2026,"fiscal_quarter":2,"period_end_date":"2026-06-30",\
                        "revenue":"1308000000.000000","gross_profit":null,\
                        "net_income":"561000000.000000","net_margin":"42.890000"}]}]}}
                        """));
        List<RobinhoodFinancialsService.FinancialsRow> rows = RobinhoodMcpFinancialsClient.parseRows(result, "HOOD");
        assertEquals(1, rows.size());
        assertEquals("2026-06-30", rows.get(0).periodEndDate());
        assertEquals(1_308_000_000d, rows.get(0).revenue());
        assertEquals(42.89, rows.get(0).netMarginPct());
    }
}
