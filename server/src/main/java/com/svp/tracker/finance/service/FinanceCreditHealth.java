package com.svp.tracker.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilization bands for credit health display. */
public final class FinanceCreditHealth {

    private FinanceCreditHealth() {}

    public static BigDecimal utilizationPct(BigDecimal balance, BigDecimal limit) {
        if (balance == null || limit == null || limit.signum() <= 0) {
            return null;
        }
        return balance.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal availableCredit(BigDecimal balance, BigDecimal limit) {
        if (limit == null) {
            return null;
        }
        BigDecimal bal = balance == null ? BigDecimal.ZERO : balance;
        return limit.subtract(bal);
    }

    public static String healthLabel(BigDecimal utilizationPct) {
        if (utilizationPct == null) {
            return "Unknown";
        }
        double u = utilizationPct.doubleValue();
        if (u < 30) {
            return "Excellent";
        }
        if (u < 50) {
            return "Good";
        }
        if (u < 75) {
            return "Fair";
        }
        return "High";
    }
}
