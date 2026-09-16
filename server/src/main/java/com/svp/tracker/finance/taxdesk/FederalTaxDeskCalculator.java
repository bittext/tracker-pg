package com.svp.tracker.finance.taxdesk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 2026 Form 1040 / 1040-ES working-paper math for married filing jointly (Texas).
 *
 * <p>Brackets, standard deduction, and NIIT threshold match the 13 Sep 2026 household estimate
 * reference. Not a filed return and not tax advice.
 */
public final class FederalTaxDeskCalculator {

    public static final String MFJ = "MARRIED_FILING_JOINTLY";
    public static final BigDecimal STD_DED_MFJ_2026 = new BigDecimal("32200");
    public static final BigDecimal NIIT_THRESHOLD_MFJ = new BigDecimal("250000");
    public static final BigDecimal NIIT_RATE = new BigDecimal("0.038");
    public static final BigDecimal CAPITAL_LOSS_ORDINARY_LIMIT = new BigDecimal("3000");
    public static final BigDecimal CURRENT_YEAR_SAFE_PCT = new BigDecimal("0.90");
    public static final BigDecimal PRIOR_YEAR_SAFE_PCT_HIGH_AGI = new BigDecimal("1.10");
    public static final BigDecimal PRIOR_YEAR_SAFE_PCT = new BigDecimal("1.00");
    public static final BigDecimal HIGH_AGI_THRESHOLD = new BigDecimal("150000");
    /** Approximate IRS individual underpayment rate used for exposure, not Form 2210 interest. */
    public static final BigDecimal UNDERPAYMENT_RATE = new BigDecimal("0.07");
    /**
     * Whole-token IRS / 1040-ES / estimated tax. {@code String.contains("irs")} also matches
     * "first", which pulled brokerage transfers into Tax desk payments.
     */
    private static final Pattern IRS_TOKEN =
            Pattern.compile("(?<![a-z0-9])irs(?![a-z0-9])|1040-?es|estimated\\s+tax");

    private static final List<Bracket> MFJ_2026 = List.of(
            new Bracket(new BigDecimal("24800"), new BigDecimal("0.10")),
            new Bracket(new BigDecimal("100800"), new BigDecimal("0.12")),
            new Bracket(new BigDecimal("211400"), new BigDecimal("0.22")),
            new Bracket(new BigDecimal("403550"), new BigDecimal("0.24")),
            new Bracket(new BigDecimal("512800"), new BigDecimal("0.32")),
            new Bracket(new BigDecimal("768700"), new BigDecimal("0.35")),
            new Bracket(null, new BigDecimal("0.37")));

    private FederalTaxDeskCalculator() {}

    public static Result compute(Input in) {
        BigDecimal wages = nz(in.wages());
        BigDecimal otherOrdinary = nz(in.otherOrdinary());
        BigDecimal realized = nz(in.realizedCapital());
        BigDecimal stCo = nz(in.stCarryover());
        BigDecimal ltCo = nz(in.ltCarryover());
        BigDecimal carryover = stCo.add(ltCo);
        BigDecimal netCapital = realized.subtract(carryover);
        BigDecimal capitalInAgi;
        BigDecimal ordinaryLossUsed = BigDecimal.ZERO;
        BigDecimal netTaxableGain = BigDecimal.ZERO;
        if (netCapital.signum() > 0) {
            capitalInAgi = netCapital;
            netTaxableGain = netCapital;
        } else {
            ordinaryLossUsed = netCapital.abs().min(CAPITAL_LOSS_ORDINARY_LIMIT);
            capitalInAgi = ordinaryLossUsed.negate();
        }
        BigDecimal agi = wages.add(otherOrdinary).add(capitalInAgi).setScale(2, RoundingMode.HALF_UP);
        BigDecimal std = STD_DED_MFJ_2026;
        BigDecimal taxableIncome = agi.subtract(std).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal incomeTax = ordinaryTax(taxableIncome).setScale(2, RoundingMode.HALF_UP);
        BigDecimal childCredit = nz(in.childCredit()).min(incomeTax.max(BigDecimal.ZERO));
        BigDecimal taxAfterCredits = incomeTax.subtract(childCredit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nii = netTaxableGain.max(BigDecimal.ZERO);
        BigDecimal niitBase = nii.min(agi.subtract(NIIT_THRESHOLD_MFJ).max(BigDecimal.ZERO));
        BigDecimal niit = niitBase.multiply(NIIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal federalTax = taxAfterCredits.add(niit).setScale(2, RoundingMode.HALF_UP);

        BigDecimal withholding = nz(in.withholdingAnnual());
        List<Payment> payments = in.payments() == null ? List.of() : in.payments();
        BigDecimal estimatesPaid = payments.stream()
                .map(p -> nz(p.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal credits = withholding.add(estimatesPaid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal filingBalance = federalTax.subtract(credits).setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetRefund = nz(in.targetRefund());
        BigDecimal recommendedCredits = federalTax.add(targetRefund).setScale(2, RoundingMode.HALF_UP);
        BigDecimal additionalPrepay = recommendedCredits.subtract(credits).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal priorTax = nz(in.priorYearTax());
        BigDecimal priorAgi = nz(in.priorYearAgi());
        BigDecimal priorSafePct = priorAgi.compareTo(HIGH_AGI_THRESHOLD) > 0
                ? PRIOR_YEAR_SAFE_PCT_HIGH_AGI
                : PRIOR_YEAR_SAFE_PCT;
        BigDecimal priorSafe = priorTax.multiply(priorSafePct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentSafe = federalTax.multiply(CURRENT_YEAR_SAFE_PCT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rap;
        String rapBasis;
        if (priorTax.signum() <= 0) {
            rap = currentSafe;
            rapBasis = "90% of estimated current-year tax (no usable prior-year tax)";
        } else {
            rap = currentSafe.min(priorSafe);
            rapBasis = currentSafe.compareTo(priorSafe) <= 0
                    ? "90% of estimated current-year tax"
                    : (priorSafePct.stripTrailingZeros().toPlainString() + " × prior-year tax (safe harbor)");
        }
        boolean safeHarborCovered = credits.compareTo(rap) >= 0;
        boolean refundTargetCovered = filingBalance.compareTo(targetRefund.negate()) <= 0;

        LocalDate asOf = in.asOf();
        int taxYear = in.taxYear();
        List<Quarter> quarters = buildQuarters(taxYear, asOf, rap, withholding, payments, additionalPrepay);
        BigDecimal penalty = estimatePenalty(quarters, asOf, taxYear);

        String riskLevel;
        String headline;
        if (refundTargetCovered && safeHarborCovered) {
            riskLevel = "REFUND_TRACK";
            headline = "On track for a refund at filing, with estimated-tax safe harbor covered.";
        } else if (safeHarborCovered) {
            riskLevel = "SAFE_HARBOR";
            headline = "Safe harbor looks covered, but filing day still projects a balance due unless more is prepaid.";
        } else if (hasPastShortfall(quarters, asOf)) {
            riskLevel = "PENALTY_RISK";
            headline = "Required installments already look short — underpayment-penalty exposure is open.";
        } else {
            riskLevel = "CATCH_UP";
            headline = "Safe harbor is not covered yet; remaining quarter(s) can still catch it up if paid on time.";
        }

        return new Result(
                wages.setScale(2, RoundingMode.HALF_UP),
                otherOrdinary.setScale(2, RoundingMode.HALF_UP),
                realized.setScale(2, RoundingMode.HALF_UP),
                carryover.setScale(2, RoundingMode.HALF_UP),
                netTaxableGain.setScale(2, RoundingMode.HALF_UP),
                ordinaryLossUsed.setScale(2, RoundingMode.HALF_UP),
                agi,
                std,
                taxableIncome,
                incomeTax,
                childCredit.setScale(2, RoundingMode.HALF_UP),
                taxAfterCredits,
                niit,
                federalTax,
                withholding.setScale(2, RoundingMode.HALF_UP),
                estimatesPaid,
                credits,
                filingBalance,
                targetRefund.setScale(2, RoundingMode.HALF_UP),
                additionalPrepay,
                rap,
                rapBasis,
                priorSafe,
                currentSafe,
                safeHarborCovered,
                refundTargetCovered,
                penalty,
                riskLevel,
                headline,
                quarters);
    }

    public static BigDecimal ordinaryTax(BigDecimal taxableIncome) {
        BigDecimal remaining = nz(taxableIncome).max(BigDecimal.ZERO);
        if (remaining.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (Bracket b : MFJ_2026) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal width = b.cap() == null ? remaining : b.cap().subtract(lower).max(BigDecimal.ZERO);
            BigDecimal slice = remaining.min(width);
            tax = tax.add(slice.multiply(b.rate()));
            remaining = remaining.subtract(slice);
            if (b.cap() != null) {
                lower = b.cap();
            }
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    public static LocalDate estimatedDue(int taxYear, int quarter) {
        LocalDate raw =
                switch (quarter) {
                    case 1 -> LocalDate.of(taxYear, Month.APRIL, 15);
                    case 2 -> LocalDate.of(taxYear, Month.JUNE, 15);
                    case 3 -> LocalDate.of(taxYear, Month.SEPTEMBER, 15);
                    case 4 -> LocalDate.of(taxYear + 1, Month.JANUARY, 15);
                    default -> throw new IllegalArgumentException("quarter " + quarter);
                };
        return nextBusinessDay(raw);
    }

    public static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY
                || d.getDayOfWeek() == DayOfWeek.SUNDAY
                || FEDERAL_HOLIDAYS.contains(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public static BigDecimal annualize(BigDecimal ytd, LocalDate asOf, int taxYear) {
        if (ytd == null || ytd.signum() <= 0 || asOf == null) {
            return nz(ytd);
        }
        int day = asOf.getYear() == taxYear ? asOf.getDayOfYear() : (asOf.getYear() > taxYear ? 365 : 1);
        int yearDays = LocalDate.of(taxYear, 12, 31).lengthOfYear();
        if (day <= 0) {
            day = 1;
        }
        return ytd.multiply(BigDecimal.valueOf(yearDays))
                .divide(BigDecimal.valueOf(day), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal projectedAmount(BigDecimal ytd, BigDecimal annual, LocalDate asOf, int taxYear) {
        if (annual != null && annual.signum() > 0) {
            return annual.setScale(2, RoundingMode.HALF_UP);
        }
        return annualize(ytd, asOf, taxYear);
    }

    public static boolean looksLikeIrs(String... parts) {
        String blob = String.join(" ", parts).toLowerCase(Locale.ROOT);
        if (blob.isBlank() || blob.contains("india")) {
            return false;
        }
        String title = parts.length > 0 && parts[0] != null ? parts[0].toLowerCase(Locale.ROOT) : "";
        if (looksLikeInternalCashMove(blob) && !IRS_TOKEN.matcher(title).find()) {
            return false;
        }
        return IRS_TOKEN.matcher(blob).find();
    }

    /** Brokerage/account moves (e.g. Transfer: Agentic), not 1040-ES deposits. */
    public static boolean looksLikeInternalCashMove(String... parts) {
        String blob = String.join(" ", parts).toLowerCase(Locale.ROOT);
        if (!blob.contains("transfer")) {
            return false;
        }
        return blob.contains("agentic")
                || blob.contains("individual")
                || blob.contains("robinhood")
                || blob.contains("ammu")
                || blob.contains("3370")
                || blob.contains("3550");
    }

    public static BigDecimal parseMoney(String... texts) {
        if (texts == null) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\$\\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?|[0-9]+(?:[.,][0-9]{2})?)");
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return parseAmountToken(m.group(1));
            }
        }
        return null;
    }

    public static BigDecimal parseAmountToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        int lastComma = t.lastIndexOf(',');
        int lastDot = t.lastIndexOf('.');
        int lastSep = Math.max(lastComma, lastDot);
        int decimalAt = -1;
        if (lastSep >= 0 && t.length() - lastSep == 3) {
            decimalAt = lastSep;
        }
        StringBuilder digits = new StringBuilder();
        int decAt = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (i == decimalAt && decAt < 0) {
                decAt = digits.length();
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        String n = digits.toString();
        if (decAt >= 0) {
            String whole = decAt == 0 ? "0" : n.substring(0, decAt);
            String frac = n.substring(decAt);
            if (frac.length() > 2) {
                frac = frac.substring(0, 2);
            } else if (frac.length() < 2) {
                frac = frac + "0".repeat(2 - frac.length());
            }
            return new BigDecimal(whole + "." + frac);
        }
        return new BigDecimal(n).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Quarter> buildQuarters(
            int taxYear,
            LocalDate asOf,
            BigDecimal rap,
            BigDecimal withholdingAnnual,
            List<Payment> payments,
            BigDecimal additionalPrepay) {
        BigDecimal installment = rap.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
        List<LocalDate> dues = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            dues.add(estimatedDue(taxYear, q));
        }
        List<Quarter> out = new ArrayList<>();
        BigDecimal withheldCum = BigDecimal.ZERO;
        BigDecimal estCum = BigDecimal.ZERO;
        BigDecimal reqCum = BigDecimal.ZERO;
        boolean nextOpenFunded = false;
        for (int i = 0; i < 4; i++) {
            int q = i + 1;
            LocalDate due = dues.get(i);
            LocalDate prevDue = i == 0 ? LocalDate.of(taxYear, 1, 1).minusDays(1) : dues.get(i - 1);
            BigDecimal withholdingSlice = withholdingAnnual
                    .multiply(BigDecimal.valueOf(q))
                    .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP)
                    .subtract(withheldCum)
                    .setScale(2, RoundingMode.HALF_UP);
            withheldCum = withheldCum.add(withholdingSlice);
            BigDecimal estSlice = BigDecimal.ZERO;
            for (Payment p : payments) {
                if (p.paidOn() == null || p.amount() == null) {
                    continue;
                }
                boolean inWindow = !p.paidOn().isAfter(due) && p.paidOn().isAfter(prevDue);
                if (inWindow) {
                    estSlice = estSlice.add(p.amount());
                }
            }
            estSlice = estSlice.setScale(2, RoundingMode.HALF_UP);
            estCum = estCum.add(estSlice);
            reqCum = reqCum.add(installment);
            BigDecimal paidToDate = withheldCum.add(estCum);
            BigDecimal shortfall = reqCum.subtract(paidToDate).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            boolean open = !asOf.isAfter(due);
            BigDecimal suggested = BigDecimal.ZERO;
            if (open && !nextOpenFunded) {
                suggested = additionalPrepay.setScale(2, RoundingMode.HALF_UP);
                nextOpenFunded = true;
            }
            String status;
            if (asOf.isAfter(due) && shortfall.signum() > 0) {
                status = "SHORT";
            } else if (asOf.isAfter(due)) {
                status = "MET";
            } else if (!due.minusDays(7).isAfter(asOf)) {
                status = shortfall.signum() > 0 || suggested.signum() > 0 ? "DUE_SOON" : "ON_TRACK";
            } else {
                status = "UPCOMING";
            }
            if (due.equals(asOf) && (shortfall.signum() > 0 || suggested.signum() > 0)) {
                status = "DUE_TODAY";
            }
            out.add(new Quarter(
                    q,
                    due,
                    installment,
                    reqCum.setScale(2, RoundingMode.HALF_UP),
                    withholdingSlice,
                    estSlice,
                    paidToDate.setScale(2, RoundingMode.HALF_UP),
                    shortfall,
                    suggested.setScale(2, RoundingMode.HALF_UP),
                    status));
        }
        return out;
    }

    private static BigDecimal estimatePenalty(List<Quarter> quarters, LocalDate asOf, int taxYear) {
        LocalDate filing = nextBusinessDay(LocalDate.of(taxYear + 1, Month.APRIL, 15));
        BigDecimal penalty = BigDecimal.ZERO;
        for (Quarter q : quarters) {
            if (q.shortfall().signum() <= 0 || asOf.isBefore(q.dueDate())) {
                continue;
            }
            LocalDate through = asOf.isBefore(filing) ? asOf : filing;
            long days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(q.dueDate(), through));
            penalty = penalty.add(q.shortfall()
                    .multiply(UNDERPAYMENT_RATE)
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP));
        }
        return penalty.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean hasPastShortfall(List<Quarter> quarters, LocalDate asOf) {
        for (Quarter q : quarters) {
            if (asOf.isAfter(q.dueDate()) && q.shortfall().signum() > 0) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final Set<LocalDate> FEDERAL_HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 19),
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 5, 25),
            LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 18));

    private record Bracket(BigDecimal cap, BigDecimal rate) {}

    public record Payment(LocalDate paidOn, BigDecimal amount) {}

    public record Input(
            String filingStatus,
            BigDecimal wages,
            BigDecimal otherOrdinary,
            BigDecimal realizedCapital,
            BigDecimal stCarryover,
            BigDecimal ltCarryover,
            BigDecimal childCredit,
            BigDecimal withholdingAnnual,
            List<Payment> payments,
            BigDecimal priorYearAgi,
            BigDecimal priorYearTax,
            BigDecimal targetRefund,
            LocalDate asOf,
            int taxYear) {}

    public record Quarter(
            int quarter,
            LocalDate dueDate,
            BigDecimal requiredInstallment,
            BigDecimal requiredToDate,
            BigDecimal withholdingCredit,
            BigDecimal estimateCredit,
            BigDecimal paidToDate,
            BigDecimal shortfall,
            BigDecimal suggestedPayment,
            String status) {}

    public record Result(
            BigDecimal wages,
            BigDecimal otherOrdinary,
            BigDecimal realizedCapital,
            BigDecimal carryoverApplied,
            BigDecimal netTaxableGain,
            BigDecimal ordinaryLossUsed,
            BigDecimal agi,
            BigDecimal standardDeduction,
            BigDecimal taxableIncome,
            BigDecimal incomeTax,
            BigDecimal childCredit,
            BigDecimal taxAfterCredits,
            BigDecimal niit,
            BigDecimal federalTax,
            BigDecimal withholding,
            BigDecimal estimatesPaid,
            BigDecimal totalCredits,
            BigDecimal filingBalance,
            BigDecimal targetRefund,
            BigDecimal additionalPrepay,
            BigDecimal requiredAnnualPayment,
            String rapBasis,
            BigDecimal priorYearSafeHarbor,
            BigDecimal currentYearSafeHarbor,
            boolean safeHarborCovered,
            boolean refundTargetCovered,
            BigDecimal penaltyExposure,
            String riskLevel,
            String riskHeadline,
            List<Quarter> quarters) {}
}
