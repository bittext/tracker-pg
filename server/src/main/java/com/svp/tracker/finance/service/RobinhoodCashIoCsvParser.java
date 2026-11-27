package com.svp.tracker.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the two CSV layouts that can be imported into Inputs / Outputs:
 * <ul>
 *   <li>ACTIVITY: Robinhood's "Account activity" export (Activity Date, Process Date, Settle Date, Instrument,
 *       Description, Trans Code, Quantity, Price, Amount).
 *   <li>TRANSFERS: a transfers-history CSV (requested_date, completed_date, direction, other_account, amount_usd,
 *       status, ...).
 * </ul>
 * Only cash movements become rows; trades, dividends, rewards and the like are counted as ignored.
 */
final class RobinhoodCashIoCsvParser {

    static final String FORMAT_ACTIVITY = "ACTIVITY";
    static final String FORMAT_TRANSFERS = "TRANSFERS";
    static final int MAX_ROWS = 20_000;

    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
    private static final Pattern ENDING_IN = Pattern.compile("account ending in (\\d{3,8})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL = Pattern.compile(
            "internal cash transfer from (\\d+) to (\\d+)", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal INSTANT_FEE_RATE = new BigDecimal("0.0175");

    private RobinhoodCashIoCsvParser() {}

    record Row(
            LocalDate requestedDate,
            LocalDate completedDate,
            String direction,
            BigDecimal amount,
            String counterparty,
            String note,
            String source) {

        LocalDate dateFor(boolean requested) {
            return requested ? requestedDate : completedDate;
        }
    }

    record Skipped(String source, String reason) {}

    record Result(String format, List<Row> rows, List<Skipped> skipped, int ignored) {}

    static Result parse(String csv, String accountSuffix, List<String> knownParties) {
        List<List<String>> table = readCsv(csv == null ? "" : csv);
        if (table.size() < 2) {
            throw bad("The file has no rows to import.");
        }
        if (table.size() > MAX_ROWS) {
            throw bad("The file has more than " + MAX_ROWS + " rows.");
        }
        List<String> header = table.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        List<List<String>> body = table.subList(1, table.size());
        if (header.contains("trans code") && header.contains("activity date") && header.contains("amount")) {
            return parseActivity(header, body, accountSuffix, knownParties);
        }
        if (header.contains("requested_date") && header.contains("direction") && header.contains("amount_usd")) {
            return parseTransfers(header, body, knownParties);
        }
        throw bad("This file isn't recognized. Use Robinhood's Account activity CSV, or a transfers history CSV with "
                + "requested_date, completed_date, direction, other_account, amount_usd and status columns.");
    }

    // ---- Account activity export -------------------------------------------------------------------------------

    private static final class Cand {
        final int line;
        final LocalDate activity;
        final LocalDate settle;
        final String code;
        final String desc;
        BigDecimal amount;
        String extraNote = "";
        boolean removed;

        Cand(int line, LocalDate activity, LocalDate settle, String code, String desc, BigDecimal amount) {
            this.line = line;
            this.activity = activity;
            this.settle = settle == null ? activity : settle;
            this.code = code;
            this.desc = desc;
            this.amount = amount;
        }

        String source() {
            return activity + " " + code + " " + desc.replace('\n', ' ').trim() + " " + (amount == null ? "" : amount);
        }
    }

    private static Result parseActivity(
            List<String> header, List<List<String>> body, String accountSuffix, List<String> known) {
        int iAct = header.indexOf("activity date");
        int iSettle = header.indexOf("settle date");
        int iDesc = header.indexOf("description");
        int iCode = header.indexOf("trans code");
        int iAmt = header.indexOf("amount");
        List<Cand> cands = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        int ignored = 0;
        int line = 1;
        for (List<String> r : body) {
            line++;
            String code = cell(r, iCode).toUpperCase(Locale.ROOT);
            if (!(code.equals("ACH") || code.equals("RTP") || code.equals("ITRF") || code.equals("XENT"))) {
                ignored++;
                continue;
            }
            LocalDate act = parseDate(cell(r, iAct));
            String desc = cell(r, iDesc);
            BigDecimal amt = parseMoney(cell(r, iAmt));
            Cand c = new Cand(line, act, parseDate(cell(r, iSettle)), code, desc, amt);
            if (act == null || amt == null) {
                skipped.add(new Skipped(c.source(), "No date or amount on this row"));
                continue;
            }
            cands.add(c);
        }

        // A cancel or reversal removes the transfer it undoes.
        for (Cand c : cands) {
            String d = c.desc.toUpperCase(Locale.ROOT);
            if (c.removed || !c.code.equals("ACH") || !(d.startsWith("ACH CANCEL") || d.startsWith("ACH REVERSAL"))) {
                continue;
            }
            Cand original = cands.stream()
                    .filter(o -> !o.removed && o != c && o.code.equals("ACH"))
                    .filter(o -> {
                        String od = o.desc.toUpperCase(Locale.ROOT);
                        return od.startsWith("ACH DEPOSIT") || od.startsWith("ACH WITHDRAWAL");
                    })
                    .filter(o -> o.amount.compareTo(c.amount.negate()) == 0)
                    .filter(o -> !o.activity.isAfter(c.activity))
                    .max(Comparator.comparing((Cand o) -> o.activity))
                    .orElse(null);
            c.removed = true;
            if (original == null) {
                skipped.add(new Skipped(c.source(), "A cancel with no matching transfer in this file"));
            } else {
                original.removed = true;
                skipped.add(new Skipped(original.source(), "Cancelled or reversed later"));
                skipped.add(new Skipped(c.source(), "Cancels the transfer above"));
            }
        }

        // Instant-transfer fees are separate rows; fold each into the withdrawal it belongs to.
        for (Cand fee : cands) {
            if (fee.removed || !fee.code.equals("RTP") || !fee.desc.toLowerCase(Locale.ROOT).contains("fee")) {
                continue;
            }
            Cand target = cands.stream()
                    .filter(o -> !o.removed && o != fee && o.code.equals("RTP"))
                    .filter(o -> !o.desc.toLowerCase(Locale.ROOT).contains("fee"))
                    .filter(o -> o.amount.signum() < 0 && o.activity.equals(fee.activity))
                    .min(Comparator.comparing((Cand o) -> feeMismatch(o, fee)))
                    .orElse(null);
            fee.removed = true;
            if (target == null) {
                skipped.add(new Skipped(fee.source(), "An instant-transfer fee with no withdrawal on the same day"));
            } else {
                target.amount = target.amount.add(fee.amount);
                target.extraNote = " (includes " + fee.amount.abs().setScale(2, RoundingMode.HALF_UP) + " instant fee)";
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Cand c : cands) {
            if (c.removed) {
                continue;
            }
            String direction = c.amount.signum() >= 0 ? "IN" : "OUT";
            String party = counterpartyFor(c, accountSuffix);
            String note = "Robinhood activity: " + c.desc.replace('\n', ' ').trim() + c.extraNote;
            rows.add(new Row(
                    c.activity,
                    c.settle,
                    direction,
                    c.amount.abs().setScale(2, RoundingMode.HALF_UP),
                    party == null ? null : resolveParty(party, known),
                    note,
                    c.source()));
        }
        return new Result(FORMAT_ACTIVITY, rows, skipped, ignored);
    }

    private static BigDecimal feeMismatch(Cand withdrawal, Cand fee) {
        BigDecimal gross = withdrawal.amount.abs().add(fee.amount.abs());
        BigDecimal expected = gross.multiply(INSTANT_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return expected.subtract(fee.amount.abs()).abs();
    }

    private static String counterpartyFor(Cand c, String accountSuffix) {
        String d = c.desc.replace('\n', ' ').trim();
        String low = d.toLowerCase(Locale.ROOT);
        switch (c.code) {
            case "RTP": {
                Matcher m = ENDING_IN.matcher(d);
                return m.find() ? "Bank account ••••" + m.group(1) : null;
            }
            case "XENT":
                return low.contains("spending") ? "Robinhood Spending" : null;
            case "ITRF": {
                if (low.contains("roth ira")) {
                    return "Roth IRA";
                }
                Matcher m = INTERNAL.matcher(d);
                if (m.find()) {
                    String a = m.group(1);
                    String b = m.group(2);
                    String other = a.endsWith(accountSuffix) ? b : (b.endsWith(accountSuffix) ? a : null);
                    return other == null ? "Another Robinhood account" : "Robinhood account ••••" + last4(other);
                }
                return low.contains("brokerage to brokerage") ? "Another Robinhood account" : null;
            }
            default:
                return null;
        }
    }

    // ---- Transfers history CSV ---------------------------------------------------------------------------------

    private static Result parseTransfers(List<String> header, List<List<String>> body, List<String> known) {
        int iReq = header.indexOf("requested_date");
        int iDone = header.indexOf("completed_date");
        int iDir = header.indexOf("direction");
        int iOther = header.indexOf("other_account");
        int iAmt = header.indexOf("amount_usd");
        int iStatus = header.indexOf("status");
        int iType = header.indexOf("transfer_type");
        List<Row> rows = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (List<String> r : body) {
            LocalDate req = parseDate(cell(r, iReq));
            LocalDate done = iDone >= 0 ? parseDate(cell(r, iDone)) : null;
            String dir = cell(r, iDir).toUpperCase(Locale.ROOT);
            BigDecimal amt = parseMoney(cell(r, iAmt));
            String other = cell(r, iOther);
            String source = (req == null ? "?" : req.toString()) + " " + dir + " " + other + " " + (amt == null ? "" : amt);
            String status = iStatus >= 0 ? cell(r, iStatus) : "Completed";
            if (!status.isEmpty() && !status.equalsIgnoreCase("completed")) {
                skipped.add(new Skipped(source, "Status is " + status));
                continue;
            }
            if (req == null || amt == null || amt.signum() <= 0 || !(dir.equals("IN") || dir.equals("OUT"))) {
                skipped.add(new Skipped(source, "Missing or invalid date, direction or amount"));
                continue;
            }
            String type = iType >= 0 ? cell(r, iType) : "";
            String note = "Robinhood history" + (type.isEmpty() ? "" : ": " + type);
            rows.add(new Row(
                    req,
                    done == null ? req : done,
                    dir,
                    amt.setScale(2, RoundingMode.HALF_UP),
                    other.isEmpty() ? null : resolveParty(other, known),
                    note,
                    source));
        }
        return new Result(FORMAT_TRANSFERS, rows, skipped, 0);
    }

    // ---- Party names -------------------------------------------------------------------------------------------

    /**
     * Maps the bank names Robinhood uses to the names you already use on entries, so one bank isn't spread over
     * several spellings. Falls back to the text as given.
     */
    static String resolveParty(String raw, List<String> known) {
        String low = raw.toLowerCase(Locale.ROOT);
        String target = null;
        if (low.contains("chase") || low.contains("total checking") || low.contains("0404")) {
            target = "chase";
        } else if (low.contains("bank of america") || low.contains("adv plus") || low.contains("7933")) {
            target = "bank of america";
        } else if (low.contains("robinhood banking") || low.contains("coastal") || low.contains("6310")) {
            target = "6310";
        }
        if (target != null) {
            for (String k : known) {
                if (k.toLowerCase(Locale.ROOT).contains(target)) {
                    return k;
                }
            }
        }
        return raw.trim();
    }

    // ---- Basics ------------------------------------------------------------------------------------------------

    static List<List<String>> readCsv(String text) {
        String s = text.startsWith("\uFEFF") ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        if (row.size() == 1 && row.get(0).isBlank()) {
            return;
        }
        rows.add(row);
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        try {
            return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
        } catch (DateTimeParseException ignored) {
            // fall through to M/d/yyyy
        }
        try {
            return LocalDate.parse(t, US_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Reads "$1,234.50", "-1234.5" and "($1,234.50)"; null when blank or unreadable. */
    static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        boolean negative = t.startsWith("(") && t.endsWith(")");
        t = t.replaceAll("[()$,\\s]", "");
        try {
            BigDecimal v = new BigDecimal(t);
            return negative ? v.negate() : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cell(List<String> row, int idx) {
        return idx >= 0 && idx < row.size() ? row.get(idx).trim() : "";
    }

    private static String last4(String digits) {
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
