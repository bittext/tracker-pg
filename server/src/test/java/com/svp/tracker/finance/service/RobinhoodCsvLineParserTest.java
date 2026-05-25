package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodCsvLineParserTest {

    private static final String HEADER =
            "Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount";

    @Test
    void normalize_unquotedAmountComma_keepsTransCodeAndQuantity() {
        String line =
                "5/21/2026,5/21/2026,5/21/2026,NBIS,NBIS 5/22/2026 Call $215.00,STC,2,12.00,$4,799.73";
        List<String> normalized = RobinhoodCsvLineParser.normalizeColumns(
                RobinhoodCsvLineParser.parseCsvLine(line), 9);

        assertEquals(9, normalized.size());
        assertEquals("STC", normalized.get(5));
        assertEquals("2", normalized.get(6));
        assertEquals("12.00", normalized.get(7));
        assertEquals("$4,799.73", normalized.get(8));
    }

    @Test
    void normalize_unquotedNegativeAmountComma_keepsBtoFields() {
        String line =
                "5/21/2026,5/21/2026,5/21/2026,NBIS,NBIS 5/22/2026 Call $215.00,BTO,2,5.73,-$1,146.08";
        List<String> normalized = RobinhoodCsvLineParser.normalizeColumns(
                RobinhoodCsvLineParser.parseCsvLine(line), 9);

        assertEquals("BTO", normalized.get(5));
        assertEquals("2", normalized.get(6));
        assertEquals("5.73", normalized.get(7));
        assertEquals("-$1,146.08", normalized.get(8));
    }

    @Test
    void legacyNormalize_misreadTransWhenAmountSplitAndQtyFour() {
        List<String> raw = RobinhoodCsvLineParser.parseCsvLine(
                "5/21/2026,5/21/2026,5/21/2026,NBIS,NBIS 5/22/2026 Call $215.00,STC,4,12.00,$4,799.73");
        // Old tail logic: trans = cols[size-4] = cols[6] = quantity "4" (not STC) when amount splits.
        assertEquals("4", raw.get(raw.size() - 4));
        List<String> fixed = RobinhoodCsvLineParser.normalizeColumns(raw, 9);
        assertEquals("STC", fixed.get(5));
        assertEquals("4", fixed.get(6));
    }

    @Test
    void optionCashMismatch_whenStcQtyTwoButAmountIsFourContracts() {
        List<String> errors = new ArrayList<>();
        RobinhoodCsvLineParser.addOptionCashMismatchWarning(
                errors,
                2,
                "NBIS 5/22/2026 Call $215.00",
                new BigDecimal("2"),
                new BigDecimal("12.00"),
                new BigDecimal("4799.73"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("implies about 4"));
    }

    @Test
    void optionCashOk_whenStcQtyFourMatchesAmount() {
        List<String> errors = new ArrayList<>();
        RobinhoodCsvLineParser.addOptionCashMismatchWarning(
                errors,
                2,
                "NBIS 5/22/2026 Call $215.00",
                new BigDecimal("4"),
                new BigDecimal("12.00"),
                new BigDecimal("4799.73"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void parseNbisRows_fromUserScenario() {
        String csv =
                HEADER
                        + "\n5/21/2026,5/21/2026,5/21/2026,NBIS,NBIS 5/22/2026 Call $215.00,BTO,2,5.73,-$1,146.08"
                        + "\n5/21/2026,5/21/2026,5/21/2026,NBIS,NBIS 5/22/2026 Call $215.00,STC,4,12.00,$4,799.73";

        List<String> records = List.of(csv.split("\n"));
        List<String> headerRow = RobinhoodCsvLineParser.parseCsvLine(records.get(0));
        Map<String, Integer> headerIdx = indexHeaders(headerRow);

        List<String> errors = new ArrayList<>();
        List<String> bto =
                RobinhoodCsvLineParser.normalizeColumns(
                        RobinhoodCsvLineParser.parseCsvLine(records.get(1)), headerRow.size());
        List<String> stc =
                RobinhoodCsvLineParser.normalizeColumns(
                        RobinhoodCsvLineParser.parseCsvLine(records.get(2)), headerRow.size());

        assertEquals("2", cell(bto, headerIdx, "quantity"));
        assertEquals("4", cell(stc, headerIdx, "quantity"));
        assertEquals("STC", cell(stc, headerIdx, "trans code"));
        assertEquals("$4,799.73", cell(stc, headerIdx, "amount"));

        RobinhoodCsvLineParser.addOptionCashMismatchWarning(
                errors,
                3,
                cell(stc, headerIdx, "description"),
                new BigDecimal(cell(stc, headerIdx, "quantity")),
                new BigDecimal(cell(stc, headerIdx, "price").replace("$", "")),
                new BigDecimal(cell(stc, headerIdx, "amount").replace("$", "").replace(",", "")));
        assertTrue(errors.isEmpty(), () -> String.join("; ", errors));
    }

    private static Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            idx.put(RobinhoodCsvLineParser.normHeader(headers.get(i)), i);
        }
        return idx;
    }

    private static String cell(List<String> cols, Map<String, Integer> idx, String... aliases) {
        for (String a : aliases) {
            Integer i = idx.get(RobinhoodCsvLineParser.normHeader(a));
            if (i != null && i >= 0 && i < cols.size()) {
                return cols.get(i).trim();
            }
        }
        return "";
    }
}
