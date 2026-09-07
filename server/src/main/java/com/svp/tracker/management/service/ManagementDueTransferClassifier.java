package com.svp.tracker.management.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Marks own-account bank moves so Due estimates and suggestions ignore them. */
public final class ManagementDueTransferClassifier {

    private ManagementDueTransferClassifier() {}

    public record TxnView(long id, long institutionId, LocalDate date, BigDecimal amount, String description) {}

    public static boolean isInternalTransferDescription(String description) {
        String desc = norm(description);
        if (desc.isEmpty()) {
            return false;
        }
        if (desc.contains("INTERNAL TRANSFER")
                || desc.contains("TRANSFER TO")
                || desc.contains("TRANSFER FROM")
                || desc.contains("ACCOUNT TRANSFER")
                || desc.contains("ONLINE TRANSFER")
                || desc.contains("ONLINE XFER")
                || desc.contains("INTRABANK")
                || desc.contains("INTRA-BANK")
                || desc.contains("INTRA FI")
                || desc.contains("INTRA-FI")) {
            return true;
        }
        return desc.contains(" XFER ") || desc.startsWith("XFER ") || desc.endsWith(" XFER") || desc.equals("XFER");
    }

    public static Set<Long> internalTransferIds(List<TxnView> rows) {
        Set<Long> ids = new HashSet<>();
        if (rows == null || rows.isEmpty()) {
            return ids;
        }
        for (TxnView row : rows) {
            if (isInternalTransferDescription(row.description())) {
                ids.add(row.id());
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            TxnView a = rows.get(i);
            if (a.amount() == null || a.amount().signum() >= 0) {
                continue;
            }
            BigDecimal need = a.amount().abs();
            for (int j = 0; j < rows.size(); j++) {
                if (i == j) {
                    continue;
                }
                TxnView b = rows.get(j);
                if (b.amount() == null || b.amount().signum() <= 0) {
                    continue;
                }
                if (a.institutionId() == b.institutionId()) {
                    continue;
                }
                if (a.date() == null || b.date() == null) {
                    continue;
                }
                long days = Math.abs(a.date().toEpochDay() - b.date().toEpochDay());
                if (days > 1) {
                    continue;
                }
                if (need.subtract(b.amount().abs()).abs().compareTo(new BigDecimal("0.02")) <= 0) {
                    ids.add(a.id());
                    ids.add(b.id());
                }
            }
        }
        return ids;
    }

    static String norm(String description) {
        return description == null ? "" : description.trim().toUpperCase(Locale.US);
    }
}
