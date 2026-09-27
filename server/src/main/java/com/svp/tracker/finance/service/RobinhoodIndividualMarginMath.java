package com.svp.tracker.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Individual ••••3370 margin figures. Official house maintenance is not on the portfolio API, so
 * requirement is scaled from Robinhood’s 2026-09-27 print (maintenance ÷ equity book).
 */
public final class RobinhoodIndividualMarginMath {

    public static final String ACCOUNT_SUFFIX = "3370";
    public static final String ACCOUNT_LABEL = "Individual a/c (...3370)";
    public static final BigDecimal ANNUAL_RATE_PERCENT = new BigDecimal("4.75");
    public static final BigDecimal NEAR_CALL_BUFFER_PERCENT = new BigDecimal("5.00");
    public static final BigDecimal HIGH_BORROW_PERCENT = new BigDecimal("50.00");
    public static final BigDecimal SEED_MAINTENANCE = new BigDecimal("163811.45");
    public static final BigDecimal SEED_EQUITY = new BigDecimal("494545.75");
    public static final String MAINTENANCE_SOURCE_ESTIMATED = "ESTIMATED";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private RobinhoodIndividualMarginMath() {}

    public static BigDecimal houseMaintenanceRate() {
        return SEED_MAINTENANCE.divide(SEED_EQUITY, 8, RoundingMode.HALF_UP);
    }

    public static BigDecimal marginDebit(BigDecimal cash) {
        if (cash == null || cash.signum() >= 0) {
            return ZERO;
        }
        return cash.abs().setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal borrowPercent(BigDecimal debit, BigDecimal equity) {
        BigDecimal loan = nullToZero(debit);
        if (loan.signum() <= 0) {
            return ZERO;
        }
        if (equity == null || equity.signum() <= 0) {
            return HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        return loan.multiply(HUNDRED).divide(equity, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal dailyInterest(BigDecimal debit) {
        BigDecimal loan = nullToZero(debit);
        if (loan.signum() <= 0) {
            return ZERO;
        }
        return loan.multiply(ANNUAL_RATE_PERCENT)
                .divide(HUNDRED, 8, RoundingMode.HALF_UP)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal estimatedMaintenance(BigDecimal equity) {
        if (equity == null || equity.signum() <= 0) {
            return ZERO;
        }
        return equity.multiply(houseMaintenanceRate()).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal bufferAmount(BigDecimal portfolio, BigDecimal maintenance) {
        return nullToZero(portfolio).subtract(nullToZero(maintenance)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal bufferPercent(BigDecimal buffer, BigDecimal portfolio) {
        if (portfolio == null || portfolio.signum() == 0) {
            return ZERO;
        }
        return nullToZero(buffer).multiply(HUNDRED).divide(portfolio, 2, RoundingMode.HALF_UP);
    }

    public static boolean nearCall(BigDecimal bufferPercent) {
        return bufferPercent != null && bufferPercent.compareTo(NEAR_CALL_BUFFER_PERCENT) <= 0;
    }

    public static boolean highBorrow(BigDecimal borrowPercent) {
        return borrowPercent != null && borrowPercent.compareTo(HIGH_BORROW_PERCENT) >= 0;
    }

    public static String riskStatus(BigDecimal bufferPercent) {
        if (bufferPercent == null) {
            return "UNKNOWN";
        }
        if (bufferPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return "CALL";
        }
        if (bufferPercent.compareTo(NEAR_CALL_BUFFER_PERCENT) <= 0) {
            return "NEAR_CALL";
        }
        if (bufferPercent.compareTo(new BigDecimal("10")) <= 0) {
            return "HIGH";
        }
        if (bufferPercent.compareTo(new BigDecimal("20")) <= 0) {
            return "ELEVATED";
        }
        return "LOW";
    }

    public static String riskLabel(String status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case "CALL" -> "Margin call";
            case "NEAR_CALL" -> "Near call";
            case "HIGH" -> "High risk";
            case "ELEVATED" -> "Elevated";
            case "LOW" -> "Low risk";
            default -> "Unknown";
        };
    }

    public static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    public static BigDecimal scaleMoney(BigDecimal v) {
        return nullToZero(v).setScale(2, RoundingMode.HALF_UP);
    }
}
