package com.svp.tracker.finance.service.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BankingHashUtilTest {

    @Test
    void fitBasedHashIgnoresDescriptionChanges() {
        long uid = 1L;
        long inst = 2L;
        String fit = "plaid-txn-abc123";
        String hFit = BankingHashUtil.transactionDedupeHexForOfxFit(uid, inst, fit);
        String hLegacyPending =
                BankingHashUtil.transactionDedupeHex(uid, inst, LocalDate.of(2025, 1, 2), new BigDecimal("10.00"), "Store pending | foo");
        String hLegacyPosted =
                BankingHashUtil.transactionDedupeHex(uid, inst, LocalDate.of(2025, 1, 2), new BigDecimal("10.00"), "Store foo");
        assertThat(hFit).isNotEqualTo(hLegacyPending).isNotEqualTo(hLegacyPosted);
        assertThat(hLegacyPending).isNotEqualTo(hLegacyPosted);
    }

    @Test
    void fitBasedHashRequiresNonBlankFit() {
        assertThatThrownBy(() -> BankingHashUtil.transactionDedupeHexForOfxFit(1, 2, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
