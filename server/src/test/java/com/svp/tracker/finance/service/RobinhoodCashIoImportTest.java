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

    private static final java.util.Map<String, String> ACCOUNTS =
            java.util.Map.of("3370", "Individual ••••3370", "8696", "Roth IRA ••••8696");

    private static final String TRANSFERS_HEADER =
            "requested_date,completed_date,direction,other_account,amount_usd,status,account\n";

    @Test
    void allAccountsReadsTheAccountColumnAndSkipsWhatItCannotPlace() {
        String csv = TRANSFERS_HEADER
                + "2026-11-01,2026-11-06,IN,Chase Total Checking,150.00,Completed,Individual ••••3370\n"
                + "2026-11-02,2026-11-03,OUT,Chase Total Checking,200.00,Completed,Roth IRA ••••8696\n"
                + "2026-11-04,2026-11-05,IN,Chase Total Checking,300.00,Completed,Mystery ••••1111\n"
                + "2026-11-05,2026-11-05,IN,Chase Total Checking,400.00,Completed,\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, null, KNOWN, ACCOUNTS);
        assertEquals(2, result.rows().size());
        assertEquals("3370", result.rows().get(0).accountSuffix());
        assertEquals("8696", result.rows().get(1).accountSuffix());
        assertEquals(2, result.skipped().size());
    }

    @Test
    void oneAccountKeepsOnlyItsRowsAndFillsBlankAccounts() {
        String csv = TRANSFERS_HEADER
                + "2026-11-01,2026-11-06,IN,Chase,150.00,Completed,3370\n"
                + "2026-11-02,2026-11-03,OUT,Chase,200.00,Completed,Roth IRA ••••8696\n"
                + "2026-11-05,2026-11-05,IN,Chase,400.00,Completed,\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, "3370", KNOWN, ACCOUNTS);
        assertEquals(2, result.rows().size());
        assertEquals(1, result.ignored());
        assertTrue(result.rows().stream().allMatch(r -> "3370".equals(r.accountSuffix())));
    }

    @Test
    void failedRejectedAndExpiredTransfersAreNotImportedButAreReportedAsVoided() {
        String csv = TRANSFERS_HEADER
                + "2026-11-01,2026-11-01,IN,Chase,150.00,Completed,3370\n"
                + "2026-11-02,2026-11-02,IN,Chase,200.00,Failed,3370\n"
                + "2026-11-03,2026-11-03,OUT,Chase,300.00,Rejected,3370\n"
                + "2026-11-04,2026-11-04,OUT,Chase,400.00,Expired,3370\n"
                + "2026-11-05,2026-11-05,IN,Chase,500.00,Pending,3370\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, null, KNOWN, ACCOUNTS);
        assertEquals(2, result.rows().size());
        assertEquals(new BigDecimal("500.00"), result.rows().get(1).amount());
        assertEquals(3, result.voided().size());
        assertEquals(3, result.skipped().size());
    }

    @Test
    void predictTransfersAreIgnored() {
        String csv = "requested_date,completed_date,direction,other_account,amount_usd,status,description,account\n"
                + "2026-11-01,2026-11-01,OUT,Predict,100.00,Completed,Transfer from individual to Predict,3370\n"
                + "2026-11-02,2026-11-02,IN,Chase,150.00,Completed,Deposit,3370\n";
        var result = RobinhoodCashIoCsvParser.parse(csv, null, KNOWN, ACCOUNTS);
        assertEquals(1, result.rows().size());
        assertEquals(1, result.ignored());
        var activity = RobinhoodCashIoCsvParser.parse(
                HEADER + "10/1/2026,10/1/2026,10/1/2026,,Transfer to Predict,ACH,,,($100.00)\n"
                        + "10/2/2026,10/2/2026,10/2/2026,,ACH Deposit,ACH,,,$150.00\n",
                "3370",
                KNOWN,
                ACCOUNTS);
        assertEquals(1, activity.rows().size());
        assertEquals(1, activity.ignored());
    }

    @Test
    void aCancelledActivityTransferIsReportedAsVoided() {
        var result = RobinhoodCashIoCsvParser.parse(
                HEADER + "10/1/2026,10/1/2026,10/1/2026,,ACH Deposit,ACH,,,$150.00\n"
                        + "10/3/2026,10/3/2026,10/3/2026,,ACH CANCEL,ACH,,,($150.00)\n",
                "3370",
                KNOWN,
                ACCOUNTS);
        assertEquals(0, result.rows().size());
        assertEquals(1, result.voided().size());
        assertEquals(new BigDecimal("150.00"), result.voided().get(0).amount());
    }

    @Test
    void onlyUnclaimedImportedEntriesCanBeRemoved() {
        var gone = List.of(row("2026-11-02", "2026-11-02", "IN", "200.00"));
        RobinhoodAccountCashIo imported = entry("2026-11-02", "IN", "200.00");
        // an entry the voided row matches in amount and date is usable only when it is passed in (imported, unclaimed)
        assertEquals(0, RobinhoodCashIoImportService.match(gone, java.util.Arrays.asList(imported), true, new java.util.HashSet<>())[0]);
        // a hand-entered or already-claimed entry is passed as null and is never matched
        assertEquals(-1, RobinhoodCashIoImportService.match(gone, java.util.Arrays.asList((RobinhoodAccountCashIo) null), true, new java.util.HashSet<>())[0]);
    }

    @Test
    void anAccountActivityFileNeedsOneAccount() {
        assertThrows(
                ResponseStatusException.class,
                () -> RobinhoodCashIoCsvParser.parse(
                        HEADER + "10/1/2026,10/1/2026,10/1/2026,,ACH Deposit,ACH,,,$150.00\n", null, KNOWN, ACCOUNTS));
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
