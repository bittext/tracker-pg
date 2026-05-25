package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.RobinhoodStocksSummaryRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates Robinhood transaction rows into stocks-summary buckets by instrument + contract. Option legs are tracked
 * by trans code (BTO/STC/STO/BTC) instead of lumping BTC with BTO and STO with STC.
 */
final class RobinhoodStocksSummaryAggregator {

    private RobinhoodStocksSummaryAggregator() {}

    static List<RobinhoodStocksSummaryRow> aggregate(List<Map<String, Object>> rows, int financialYear) {
        Map<String, SummaryAgg> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String trans = RobinhoodFinanceService.stringCellPublic(row, "TRANS_CODE");
            TransKind kind = classifyTrans(trans);
            if (kind == TransKind.OTHER) {
                continue;
            }
            String inst = Objects.requireNonNullElse(RobinhoodFinanceService.stringCellPublic(row, "INSTRUMENT"), "")
                    .trim();
            if (inst.isEmpty()) {
                inst = "—";
            }
            String contract = Objects.requireNonNullElse(RobinhoodFinanceService.stringCellPublic(row, "DESCRIPTION"), "")
                    .trim();
            if (contract.isEmpty()) {
                contract = "—";
            }
            final String instKey = inst;
            final String contractKey = contract;
            BigDecimal qty = RobinhoodFinanceService.decimalCellPublic(row, "QUANTITY");
            BigDecimal amt = RobinhoodFinanceService.decimalCellPublic(row, "AMOUNT");
            LocalDate activity = RobinhoodFinanceService.localDateCellPublic(row, "ACTIVITY_DATE");
            String key = instKey + "\u0001" + contractKey;
            SummaryAgg agg = byKey.computeIfAbsent(key, k -> new SummaryAgg(instKey, contractKey, financialYear));
            agg.add(kind, activity, qty, amt);
        }
        List<RobinhoodStocksSummaryRow> out = new ArrayList<>(byKey.size());
        for (SummaryAgg a : byKey.values()) {
            out.add(a.toRow());
        }
        return out;
    }

    private enum TransKind {
        BTO,
        STC,
        STO,
        BTC,
        STOCK_BUY,
        STOCK_SELL,
        OTHER
    }

    private static TransKind classifyTrans(String transCode) {
        if (transCode == null) {
            return TransKind.OTHER;
        }
        return switch (transCode.trim().toUpperCase(Locale.ROOT)) {
            case "BTO" -> TransKind.BTO;
            case "STC" -> TransKind.STC;
            case "STO" -> TransKind.STO;
            case "BTC" -> TransKind.BTC;
            case "BUY" -> TransKind.STOCK_BUY;
            case "SELL" -> TransKind.STOCK_SELL;
            default -> TransKind.OTHER;
        };
    }

    private static final class SummaryAgg {
        final String instrument;
        final String contract;
        final int financialYear;
        BigDecimal btoQty = BigDecimal.ZERO;
        BigDecimal stcQty = BigDecimal.ZERO;
        BigDecimal stoQty = BigDecimal.ZERO;
        BigDecimal btcQty = BigDecimal.ZERO;
        BigDecimal stockBuyQty = BigDecimal.ZERO;
        BigDecimal stockSellQty = BigDecimal.ZERO;
        BigDecimal buyAmt = BigDecimal.ZERO;
        BigDecimal sellAmt = BigDecimal.ZERO;
        int buyLegs;
        int sellLegs;
        LocalDate firstBuy;
        LocalDate lastBuy;
        LocalDate firstSell;
        LocalDate lastSell;

        SummaryAgg(String instrument, String contract, int financialYear) {
            this.instrument = instrument;
            this.contract = contract;
            this.financialYear = financialYear;
        }

        void add(TransKind kind, LocalDate d, BigDecimal qty, BigDecimal amt) {
            BigDecimal absQty = qty == null ? BigDecimal.ZERO : qty.abs();
            switch (kind) {
                case BTO -> {
                    buyLegs++;
                    btoQty = btoQty.add(absQty);
                    addBuyCash(amt);
                    touch(d, true);
                }
                case BTC -> {
                    buyLegs++;
                    btcQty = btcQty.add(absQty);
                    addBuyCash(amt);
                    touch(d, true);
                }
                case STOCK_BUY -> {
                    buyLegs++;
                    stockBuyQty = stockBuyQty.add(absQty);
                    addBuyCash(amt);
                    touch(d, true);
                }
                case STC -> {
                    sellLegs++;
                    stcQty = stcQty.add(absQty);
                    addSellCash(amt);
                    touch(d, false);
                }
                case STO -> {
                    sellLegs++;
                    stoQty = stoQty.add(absQty);
                    addSellCash(amt);
                    touch(d, false);
                }
                case STOCK_SELL -> {
                    sellLegs++;
                    stockSellQty = stockSellQty.add(absQty);
                    addSellCash(amt);
                    touch(d, false);
                }
                default -> {}
            }
        }

        private void addBuyCash(BigDecimal amt) {
            if (amt != null) {
                buyAmt = buyAmt.add(amt);
            }
        }

        private void addSellCash(BigDecimal amt) {
            if (amt != null) {
                sellAmt = sellAmt.add(amt);
            }
        }

        private void touch(LocalDate d, boolean buy) {
            if (d == null) {
                return;
            }
            if (buy) {
                if (firstBuy == null || d.isBefore(firstBuy)) {
                    firstBuy = d;
                }
                if (lastBuy == null || d.isAfter(lastBuy)) {
                    lastBuy = d;
                }
            } else {
                if (firstSell == null || d.isBefore(firstSell)) {
                    firstSell = d;
                }
                if (lastSell == null || d.isAfter(lastSell)) {
                    lastSell = d;
                }
            }
        }

        RobinhoodStocksSummaryRow toRow() {
            BigDecimal totalBuyQty = btoQty.add(btcQty).add(stockBuyQty);
            BigDecimal totalSellQty = stcQty.add(stoQty).add(stockSellQty);
            BigDecimal net = buyAmt.add(sellAmt);
            return new RobinhoodStocksSummaryRow(
                    instrument,
                    contract,
                    financialYear,
                    totalBuyQty,
                    totalSellQty,
                    btoQty,
                    stcQty,
                    stoQty,
                    btcQty,
                    stockBuyQty,
                    stockSellQty,
                    buyAmt,
                    sellAmt,
                    net,
                    firstBuy,
                    lastBuy,
                    firstSell,
                    lastSell,
                    buyLegs,
                    sellLegs);
        }
    }
}
