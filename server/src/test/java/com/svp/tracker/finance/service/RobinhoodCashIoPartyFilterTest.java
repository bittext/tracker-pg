package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodCashIoPartyFilterTest {

    private static final String BANK = "Robinhood Coastal Banking ••••6310";
    private static final Map<String, String> LABELS =
            Map.of("3370", "Individual ••••3370", "5501", "Agentic ••••5501");

    private final RobinhoodAccountCashIo withdrawalToBank = row("3370", "OUT", "50000", BANK);
    private final RobinhoodAccountCashIo depositFromBank = row("3370", "IN", "2000", BANK);
    private final RobinhoodAccountCashIo agenticWithdrawal = row("5501", "OUT", "300", BANK);
    private final RobinhoodAccountCashIo untagged = row("3370", "OUT", "10", null);
    private final List<RobinhoodAccountCashIo> all =
            List.of(withdrawalToBank, depositFromBank, agenticWithdrawal, untagged);

    @Test
    void outputsOriginateFromOwnAccountAndReachTheCounterparty() {
        assertEquals("Individual ••••3370", RobinhoodCashIoService.originOf(withdrawalToBank, LABELS));
        assertEquals(BANK, RobinhoodCashIoService.receiverOf(withdrawalToBank, LABELS));
    }

    @Test
    void inputsOriginateFromCounterpartyAndReachOwnAccount() {
        assertEquals(BANK, RobinhoodCashIoService.originOf(depositFromBank, LABELS));
        assertEquals("Individual ••••3370", RobinhoodCashIoService.receiverOf(depositFromBank, LABELS));
    }

    @Test
    void blankFiltersKeepEverything() {
        assertEquals(all, RobinhoodCashIoService.filterByParties(all, LABELS, null, null));
        assertEquals(all, RobinhoodCashIoService.filterByParties(all, LABELS, "  ", ""));
    }

    @Test
    void originFilterPicksTransfersLeavingThatAccount() {
        var rows = RobinhoodCashIoService.filterByParties(all, LABELS, "Individual ••••3370", null);
        assertEquals(List.of(withdrawalToBank, untagged), rows);
    }

    @Test
    void receiverFilterPicksTransfersArrivingAtThatAccount() {
        var rows = RobinhoodCashIoService.filterByParties(all, LABELS, null, BANK);
        assertEquals(List.of(withdrawalToBank, agenticWithdrawal), rows);
    }

    @Test
    void bothFiltersNarrowToOneRoute() {
        var rows = RobinhoodCashIoService.filterByParties(all, LABELS, "Individual ••••3370", BANK);
        assertEquals(List.of(withdrawalToBank), rows);
    }

    @Test
    void matchingIgnoresCaseAndExtraSpaces() {
        var rows = RobinhoodCashIoService.filterByParties(all, LABELS, null, "  robinhood  coastal banking ••••6310 ");
        assertEquals(List.of(withdrawalToBank, agenticWithdrawal), rows);
    }

    @Test
    void unrecordedSideNeverMatchesANamedFilter() {
        assertNull(RobinhoodCashIoService.receiverOf(untagged, LABELS));
        assertEquals(
                List.of(), RobinhoodCashIoService.filterByParties(List.of(untagged), LABELS, null, BANK));
    }

    private static RobinhoodAccountCashIo row(String suffix, String direction, String amount, String counterparty) {
        var r = new RobinhoodAccountCashIo();
        r.setAccountSuffix(suffix);
        r.setActivityDate(LocalDate.of(2026, 10, 19));
        r.setDirection(direction);
        r.setAmount(new BigDecimal(amount));
        r.setCounterparty(counterparty);
        return r;
    }
}
