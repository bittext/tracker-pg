package com.svp.tracker.management.service;

import java.util.Locale;

/**
 * Due suggestions and history estimates come from deposit bank books only (Checking / Savings /
 * Banking), not credit cards, loans, insurance, or brokerage imports.
 */
public final class ManagementDueBankAccountSupport {

    private ManagementDueBankAccountSupport() {}

    public static boolean isBankingAccountType(String typeName) {
        String n = typeName == null ? "" : typeName.trim().toUpperCase(Locale.US);
        if (n.isEmpty() || looksNonBank(n)) {
            return false;
        }
        return n.equals("BANK")
                || n.contains("BANKING")
                || n.contains("CHECKING")
                || n.contains("SAVINGS")
                || n.contains("MONEY MARKET")
                || n.contains("DEPOSIT");
    }

    private static boolean looksNonBank(String n) {
        return n.contains("CREDIT")
                || n.contains("CARD")
                || n.contains("LOAN")
                || n.contains("MORTGAGE")
                || n.contains("INSURANCE")
                || n.contains("INVEST")
                || n.contains("BROKER")
                || n.contains("RETIREMENT")
                || n.contains("401")
                || n.contains("IRA")
                || n.contains("CRYPTO");
    }
}
