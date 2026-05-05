package com.svp.tracker.finance.service.banking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Builds a minimal OFX/QFX-style document with {@code <STMTTRN>} blocks so {@link BankingFormatParser#parse} can ingest
 * Plaid-sourced transactions the same way as bank exports.
 */
public final class BankingPlaidOfxWriter {

    private static final DateTimeFormatter DTPOSTED = DateTimeFormatter.BASIC_ISO_DATE;

    private BankingPlaidOfxWriter() {}

    public record PlaidOfxRow(
            LocalDate date,
            /** Signed amount for {@code <TRNAMT>}: positive = credit (inflow), negative = debit (outflow). */
            BigDecimal trnAmt,
            String name,
            String memo,
            String fitId) {}

    public static byte[] toQfxBytes(String bankLabel, List<PlaidOfxRow> rows) {
        String acctId = sanitizeId(bankLabel);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("OFXHEADER:100\n");
        sb.append("DATA:OFXSGML\n");
        sb.append("VERSION:102\n");
        sb.append("SECURITY:NONE\n");
        sb.append("ENCODING:UTF-8\n");
        sb.append("CHARSET:UTF-8\n");
        sb.append("COMPRESSION:NONE\n");
        sb.append("OLDFILEUID:NONE\n");
        sb.append("NEWFILEUID:NONE\n\n");
        sb.append("<OFX>\n");
        sb.append("<SIGNONMSGSRSV1>\n<SONRS>\n<STATUS>\n<CODE>0\n<SEVERITY>INFO\n</STATUS>\n");
        sb.append("<DTSERVER>").append(DTPOSTED.format(LocalDate.now())).append("120000\n");
        sb.append("</SONRS>\n</SIGNONMSGSRSV1>\n");
        sb.append("<BANKMSGSRSV1>\n<STMTTRNRS>\n<TRNUID>TRACKER-PLAID\n<STATUS><CODE>0<SEVERITY>INFO</STATUS>\n");
        sb.append("<STMTRS>\n<CURDEF>USD\n<BANKACCTFROM>\n");
        sb.append("<BANKID>000000001\n<ACCTID>")
                .append(xmlEscape(acctId))
                .append("\n<ACCTTYPE>CHECKING\n</BANKACCTFROM>\n<BANKTRANLIST>\n");
        for (PlaidOfxRow r : rows) {
            if (r.date() == null || r.trnAmt() == null) {
                continue;
            }
            String type = r.trnAmt().signum() >= 0 ? "CREDIT" : "DEBIT";
            sb.append("<STMTTRN>\n<TRNTYPE>")
                    .append(type)
                    .append("\n<DTPOSTED>")
                    .append(DTPOSTED.format(r.date()))
                    .append("120000\n<TRNAMT>")
                    .append(r.trnAmt().toPlainString())
                    .append("\n<FITID>")
                    .append(xmlEscape(r.fitId() == null ? "" : r.fitId()))
                    .append("\n<NAME>")
                    .append(xmlEscape(r.name() == null ? "" : r.name()))
                    .append("\n<MEMO>")
                    .append(xmlEscape(r.memo() == null ? "" : r.memo()))
                    .append("\n</STMTTRN>\n");
        }
        sb.append("</BANKTRANLIST>\n</STMTRS>\n</STMTTRNRS>\n</BANKMSGSRSV1>\n</OFX>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String sanitizeId(String label) {
        String s = Objects.toString(label, "plaid").replaceAll("[^a-zA-Z0-9._-]+", "_");
        return s.isBlank() ? "plaid" : s.substring(0, Math.min(64, s.length()));
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
