package com.svp.tracker.finance.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Form1040TextParserTest {

    @Test
    void parsesKeyLinesFromCuratedCorpusWithAcceptanceGate() throws IOException {
        List<Case> cases = List.of(
                new Case("tax-corpus/fill-form-2024.txt", bd("78500"), bd("11220"), bd("1230")),
                new Case("tax-corpus/software-breaks.txt", bd("95040"), bd("14830"), bd("2100")),
                new Case("tax-corpus/ocr-noise-guards.txt", bd("61400"), bd("8410"), bd("0")),
                new Case("tax-corpus/line-number-traps.txt", bd("88240"), bd("19430"), bd("0")));

        int successfulExactOrNeighbor = 0;
        int totalKeyLineExtractions = 0;

        for (Case c : cases) {
            String text = readResource(c.resourcePath());
            Form1040ParsedSummary summary = Form1040TextParser.parse(text);

            assertEquals(c.line1a(), summary.getWagesSalariesTips(), "line 1a mismatch for " + c.resourcePath());
            assertEquals(
                    c.line24(),
                    summary.getTotalTaxAfterCredits(),
                    "line 24 mismatch for " + c.resourcePath());
            assertEquals(c.line37(), summary.getAmountOwed(), "line 37 mismatch for " + c.resourcePath());

            Map<String, Form1040FieldProvenance> p = summary.getFieldProvenance();
            assertNotNull(p, "field provenance should be present");

            successfulExactOrNeighbor += isExactOrNeighbor(p.get("wagesSalariesTips")) ? 1 : 0;
            successfulExactOrNeighbor += isExactOrNeighbor(p.get("totalTaxAfterCredits")) ? 1 : 0;
            successfulExactOrNeighbor += isExactOrNeighbor(p.get("amountOwed")) ? 1 : 0;
            totalKeyLineExtractions += 3;
        }

        double ratio = (double) successfulExactOrNeighbor / totalKeyLineExtractions;
        assertTrue(ratio >= 0.95, "expected >=95% exact/neighbor key-line extraction but got " + ratio);
    }

    @Test
    void doesNotMisMapNearbyNonTargetLines() throws IOException {
        String text = readResource("tax-corpus/ocr-noise-guards.txt");
        Form1040ParsedSummary summary = Form1040TextParser.parse(text);

        // Prevent critical mis-map: line 1a must not pick line 14 value.
        assertEquals(bd("61400"), summary.getWagesSalariesTips());
        // Prevent critical mis-map: line 24 must not pick line 2A value.
        assertEquals(bd("8410"), summary.getTotalTaxAfterCredits());
    }

    private static boolean isExactOrNeighbor(Form1040FieldProvenance p) {
        return p != null && ("exact".equalsIgnoreCase(p.sourcePass()) || "neighbor".equalsIgnoreCase(p.sourcePass()));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static String readResource(String path) throws IOException {
        try (var in = Form1040TextParserTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Case(String resourcePath, BigDecimal line1a, BigDecimal line24, BigDecimal line37) {}
}
