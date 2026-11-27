package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RobinhoodCashIoImportTest {

    private static final List<String> KNOWN = List.of("Chase a/c", "Robinhood Banking ••••6310", "Chase");

    private static final String HEADER =
            "Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount\n";

    @Test
    void readsQuotedFieldsWithCommasAndLineBreaks() {
        var rows = RobinhoodCashIoCsvParser.readCsv("a,b\n\"x, y\",\"line1\nline2\"\n\"he said \"\"hi\"\"\",z\n");
        assertEquals(List.of("x, y", "line1\nline2"), rows.get(1));
        assertEquals("he said \"hi\"", rows.get(2).get(0));
    }

    @Test
    void parsesMoneyInRobinhoodStyles() {
        assertEquals(new BigDecimal("1234.50"), RobinhoodCashIoCsvParser.parseMoney("$1,234.50"));
        assertEquals(new BigDecimal("-1000.00"), RobinhoodCashIoCsvParser.parseMoney("($1,000.00)"));
        assertEquals(new BigDecimal("-5"), RobinhoodCashIoCsvParser.parseMoney("-5"));
        assertEquals(null, RobinhoodCashIoCsvParser.parseMoney(""));
    }

    @Test
    void activityExportKeepsOnlyCashMovements() {
        String csv = HEADER
                + "10/20/2026,10/20/2026,10/20/2026,,ACH Deposit,ACH,,,\"$150.00\"\n"
                + "10/20/2026,10/20/2026,10/20/2026,,ACH Withdrawal,ACH,,,\"($5,000.00)\"\n"
                + "10/21/2026,10/21/2026,10/23/2026,AAPL,Buy,Buy,1,200,($200.00)\n"
                + "10/22/2026,10/22/2026,10/22/2026,,Cash reward,MISC,,,$1.00\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN);
        assertEquals("ACTIVITY", result.format());
        assertEquals(2, result.rows().size());
        assertEquals(2, result.ignored());
        assertEquals("IN", result.rows().get(0).direction());
        assertEquals(new BigDecimal("5000.00"), result.rows().get(1).amount());
        assertEquals("OUT", result.rows().get(1).direction());
    }

    @Test
    void activityExportDropsCancelledTransfersTogetherWithTheirCancel() {
        String csv = HEADER
                + "10/19/2026,10/19/2026,10/19/2026,,ACH CANCEL,ACH,,,\"$10,000.00\"\n"
                + "10/19/2026,10/19/2026,10/19/2026,,ACH Withdrawal,ACH,,,\"($10,000.00)\"\n"
                + "10/20/2026,10/20/2026,10/21/2026,,ACH Withdrawal,ACH,,,\"($3,000.00)\"\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN);
        assertEquals(1, result.rows().size());
        assertEquals(new BigDecimal("3000.00"), result.rows().get(0).amount());
        assertEquals(2, result.skipped().size());
    }

    @Test
    void instantTransferFeeIsFoldedIntoTheWithdrawal() {
        String csv = HEADER
                + "10/20/2026,10/20/2026,10/20/2026,,Instant bank transfer - withdrawal fee,RTP,,,($87.50)\n"
                + "10/20/2026,10/20/2026,10/20/2026,,Instant bank transfer - account ending in 0404,RTP,,,\"($4,912.50)\"\n"
                + "10/20/2026,10/20/2026,10/20/2026,,Instant bank transfer - withdrawal fee,RTP,,,($35.00)\n"
                + "10/20/2026,10/20/2026,10/20/2026,,Instant bank transfer - account ending in 7933,RTP,,,\"($1,965.00)\"\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN);
        assertEquals(2, result.rows().size());
        var chase = result.rows().stream().filter(r -> r.amount().compareTo(new BigDecimal("5000")) == 0).findFirst();
        var boa = result.rows().stream().filter(r -> r.amount().compareTo(new BigDecimal("2000")) == 0).findFirst();
        assertTrue(chase.isPresent(), "5,000 total = 4,912.50 + 87.50 fee");
        assertTrue(boa.isPresent(), "2,000 total = 1,965.00 + 35.00 fee");
        assertEquals("Chase a/c", chase.get().counterparty());
    }

    @Test
    void settleDateIsTheCompletedDate() {
        String csv = HEADER + "10/20/2026,10/20/2026,10/26/2026,,ACH Withdrawal,ACH,,,($3,000.00)\n";
        var row = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN).rows().get(0);
        assertEquals(LocalDate.of(2026, 10, 20), row.dateFor(true));
        assertEquals(LocalDate.of(2026, 10, 26), row.dateFor(false));
    }

    @Test
    void transfersHistoryCsvSkipsNonCompletedAndNamesBanksYouAlreadyUse() {
        String csv = "requested_date,completed_date,direction,other_account,amount_usd,status\n"
                + "2026-10-19,2026-10-23,OUT,Robinhood Banking ••••6310 (Coastal Community Bank),50000.00,Completed\n"
                + "2026-10-18,2026-10-21,IN,Chase Total Checking,1500.00,Expired\n"
                + "2026-10-05,2026-10-06,IN,Chase Total Checking,150.00,Completed\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN);
        assertEquals("TRANSFERS", result.format());
        assertEquals(2, result.rows().size());
        assertEquals(1, result.skipped().size());
        assertEquals("Robinhood Banking ••••6310", result.rows().get(0).counterparty());
        assertEquals("Chase a/c", result.rows().get(1).counterparty());
    }

    @Test
    void unknownBankNamesStayAsGiven() {
        assertEquals("Fidelity Cash", RobinhoodCashIoCsvParser.resolveParty(" Fidelity Cash ", KNOWN));
    }

    @Test
    void unrecognizedFilesAreRejected() {
        assertThrows(
                ResponseStatusException.class,
                () -> RobinhoodCashIoCsvParser.parse("foo,bar\n1,2\n", "3370", KNOWN));
        assertThrows(
                ResponseStatusException.class, () -> RobinhoodCashIoCsvParser.parse("only a header\n", "3370", KNOWN));
    }

    @Test
    void duplicatesAreMatchedOnDirectionAmountAndNearbyDateAndConsumedOnce() {
        var rows = List.of(
                row("2026-10-19", "2026-10-23", "OUT", "50000.00"),
                row("2026-10-20", "2026-10-26", "OUT", "3000.00"),
                row("2026-10-05", "2026-10-06", "IN", "150.00"),
                row("2026-10-14", "2026-10-14", "IN", "150.00"));
        var existing = List.of(
                entry("2026-10-23", "OUT", "50000.00"), // 4 days from the request date: same transfer
                entry("2026-10-06", "IN", "150.00"),
                entry("2026-10-20", "IN", "3000.00")); // wrong direction: not a match
        var result = RobinhoodCashIoImportService.classify(rows, existing, true);
        assertEquals(List.of(false, true, false, true), result);
    }

    @Test
    void sameAmountFarApartIsNotADuplicate() {
        var rows = List.of(row("2026-10-27", "2026-10-27", "IN", "150.00"));
        var existing = List.of(entry("2026-09-27", "IN", "150.00"));
        assertEquals(List.of(true), RobinhoodCashIoImportService.classify(rows, existing, true));
    }

    private static RobinhoodCashIoCsvParser.Row row(String requested, String completed, String dir, String amount) {
        return new RobinhoodCashIoCsvParser.Row(
                LocalDate.parse(requested), LocalDate.parse(completed), dir, new BigDecimal(amount), null, "", "");
    }

    private static RobinhoodAccountCashIo entry(String date, String dir, String amount) {
        var e = new RobinhoodAccountCashIo();
        e.setActivityDate(LocalDate.parse(date));
        e.setDirection(dir);
        e.setAmount(new BigDecimal(amount));
        return e;
    }
}
