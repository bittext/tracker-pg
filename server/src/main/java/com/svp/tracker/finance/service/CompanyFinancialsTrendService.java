package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.CompanyFinancialsQuarterDto;
import com.svp.tracker.finance.dto.CompanyFinancialsTrendDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Deterministic, explainable read of the trailing quarterly trend — NOT an analyst consensus
 * forecast. Every verdict cites the numbers it was computed from.
 */
@Service
public class CompanyFinancialsTrendService {

    private static final int MIN_QUARTERS = 4;
    private static final double REVENUE_ACCEL_THRESHOLD_PP = 2.0;
    private static final double MARGIN_TREND_THRESHOLD_PP = 1.0;
    private static final double EPS_SURPRISE_NOISE_PCT = 1.0;
    private static final int STREAK_THRESHOLD = 3;

    /** @param quarters oldest -> newest */
    public CompanyFinancialsTrendDto assess(List<CompanyFinancialsQuarterDto> quarters) {
        List<String> warnings = new ArrayList<>();
        int incomeQuarters = (int)
                quarters.stream().filter(q -> q.revenue() != null && q.netIncome() != null).count();
        int epsQuarters = (int)
                quarters.stream().filter(q -> q.epsActual() != null && q.epsEstimate() != null).count();

        if (incomeQuarters < MIN_QUARTERS && epsQuarters < MIN_QUARTERS) {
            return new CompanyFinancialsTrendDto(
                    "Insufficient history for a trend read",
                    null,
                    null,
                    null,
                    null,
                    "Fewer than " + MIN_QUARTERS + " quarters of financial data are available for this symbol.",
                    warnings);
        }

        RevenueRead revenueRead = incomeQuarters >= MIN_QUARTERS ? readRevenue(quarters) : null;
        MarginRead marginRead = incomeQuarters >= MIN_QUARTERS ? readMargin(quarters) : null;
        EpsRead epsRead = epsQuarters >= MIN_QUARTERS ? readEps(quarters) : null;

        if (revenueRead == null && marginRead == null) {
            warnings.add("Revenue/net income history not available; trend based on EPS surprises only.");
        }
        if (epsRead == null) {
            warnings.add("EPS estimate/actual data not available for this symbol.");
        }

        int score = 0;
        score += revenueRead != null ? revenueScore(revenueRead.trend) : 0;
        score += marginRead != null ? marginScore(marginRead.trend) : 0;
        score += epsRead != null ? epsScore(epsRead.trend) : 0;

        String verdict;
        if (score >= 2) {
            verdict = "Improving";
        } else if (score <= -2) {
            verdict = "Declining";
        } else {
            verdict = "Mixed";
        }

        String narrative = buildNarrative(revenueRead, marginRead, epsRead);

        return new CompanyFinancialsTrendDto(
                verdict,
                score,
                revenueRead != null ? revenueRead.trend : null,
                marginRead != null ? marginRead.trend : null,
                epsRead != null ? epsRead.trend : null,
                narrative,
                warnings);
    }

    private record RevenueRead(String trend, double avgRecentYoYPct, double avgPriorYoYPct) {}

    private record MarginRead(String trend, double avgRecentMarginPct, double avgPriorMarginPct) {}

    private record EpsRead(String trend, int beatStreak, int missStreak, int quartersConsidered) {}

    private RevenueRead readRevenue(List<CompanyFinancialsQuarterDto> quarters) {
        List<Double> yoyGrowths = new ArrayList<>();
        for (int i = quarters.size() - 1; i >= 4; i--) {
            CompanyFinancialsQuarterDto q = quarters.get(i);
            CompanyFinancialsQuarterDto prior = quarters.get(i - 4);
            if (q.revenue() == null || prior.revenue() == null || prior.revenue() == 0) {
                continue;
            }
            yoyGrowths.add((q.revenue() - prior.revenue()) / prior.revenue() * 100);
        }
        if (yoyGrowths.isEmpty()) {
            return null;
        }
        // yoyGrowths[0] is the most recent quarter's YoY growth.
        List<Double> recent = yoyGrowths.subList(0, Math.min(4, yoyGrowths.size()));
        List<Double> prior = yoyGrowths.size() > 4
                ? yoyGrowths.subList(4, Math.min(8, yoyGrowths.size()))
                : List.of();
        double avgRecent = average(recent);
        double avgPrior = prior.isEmpty() ? avgRecent : average(prior);
        double delta = avgRecent - avgPrior;

        String trend;
        if (avgRecent < 0) {
            trend = "contracting";
        } else if (!prior.isEmpty() && delta > REVENUE_ACCEL_THRESHOLD_PP) {
            trend = "accelerating";
        } else if (!prior.isEmpty() && delta < -REVENUE_ACCEL_THRESHOLD_PP) {
            trend = "decelerating";
        } else {
            trend = "stable";
        }
        return new RevenueRead(trend, avgRecent, avgPrior);
    }

    private MarginRead readMargin(List<CompanyFinancialsQuarterDto> quarters) {
        List<Double> margins = new ArrayList<>();
        for (int i = quarters.size() - 1; i >= 0; i--) {
            CompanyFinancialsQuarterDto q = quarters.get(i);
            if (q.netMarginPct() != null) {
                margins.add(q.netMarginPct());
            }
        }
        if (margins.isEmpty()) {
            return null;
        }
        List<Double> recent = margins.subList(0, Math.min(4, margins.size()));
        List<Double> prior = margins.size() > 4 ? margins.subList(4, Math.min(8, margins.size())) : List.of();
        double avgRecent = average(recent);
        double avgPrior = prior.isEmpty() ? avgRecent : average(prior);
        double deltaPp = avgRecent - avgPrior;

        String trend;
        if (prior.isEmpty()) {
            trend = "flat";
        } else if (deltaPp > MARGIN_TREND_THRESHOLD_PP) {
            trend = "expanding";
        } else if (deltaPp < -MARGIN_TREND_THRESHOLD_PP) {
            trend = "compressing";
        } else {
            trend = "flat";
        }
        return new MarginRead(trend, avgRecent, avgPrior);
    }

    private EpsRead readEps(List<CompanyFinancialsQuarterDto> quarters) {
        List<String> calls = new ArrayList<>(); // most recent first: "beat" | "miss" | "in-line"
        for (int i = quarters.size() - 1; i >= 0; i--) {
            CompanyFinancialsQuarterDto q = quarters.get(i);
            if (q.epsActual() == null || q.epsEstimate() == null || q.epsEstimate() == 0) {
                continue;
            }
            double surprisePct = (q.epsActual() - q.epsEstimate()) / Math.abs(q.epsEstimate()) * 100;
            if (surprisePct > EPS_SURPRISE_NOISE_PCT) {
                calls.add("beat");
            } else if (surprisePct < -EPS_SURPRISE_NOISE_PCT) {
                calls.add("miss");
            } else {
                calls.add("in-line");
            }
            if (calls.size() >= 4) {
                break;
            }
        }
        if (calls.isEmpty()) {
            return null;
        }
        int beatStreak = 0;
        for (String c : calls) {
            if (!"beat".equals(c)) {
                break;
            }
            beatStreak++;
        }
        int missStreak = 0;
        for (String c : calls) {
            if (!"miss".equals(c)) {
                break;
            }
            missStreak++;
        }
        String trend;
        if (beatStreak >= STREAK_THRESHOLD) {
            trend = "consistent-beats";
        } else if (missStreak >= STREAK_THRESHOLD) {
            trend = "consistent-misses";
        } else {
            trend = "mixed";
        }
        return new EpsRead(trend, beatStreak, missStreak, calls.size());
    }

    private static int revenueScore(String trend) {
        return switch (trend) {
            case "contracting" -> -2;
            case "decelerating" -> -1;
            case "accelerating" -> 1;
            default -> 0;
        };
    }

    private static int marginScore(String trend) {
        return switch (trend) {
            case "compressing" -> -1;
            case "expanding" -> 1;
            default -> 0;
        };
    }

    private static int epsScore(String trend) {
        return switch (trend) {
            case "consistent-misses" -> -1;
            case "consistent-beats" -> 1;
            default -> 0;
        };
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%+.1f%%", v);
    }

    private static String pp(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    private String buildNarrative(RevenueRead revenue, MarginRead margin, EpsRead eps) {
        List<String> parts = new ArrayList<>();
        if (revenue != null) {
            parts.add("Revenue growth is "
                    + revenue.trend
                    + " ("
                    + pct(revenue.avgRecentYoYPct)
                    + " avg YoY over the last 4 quarters vs. "
                    + pct(revenue.avgPriorYoYPct)
                    + " the prior 4)");
        }
        if (margin != null) {
            parts.add("net margin is "
                    + margin.trend
                    + " (from "
                    + pp(margin.avgPriorMarginPct)
                    + " to "
                    + pp(margin.avgRecentMarginPct)
                    + ")");
        }
        if (eps != null) {
            if (eps.beatStreak >= 1 || eps.missStreak >= 1) {
                String streakDesc = eps.beatStreak > 0
                        ? "beat estimates " + eps.beatStreak + " straight quarter(s)"
                        : "missed estimates " + eps.missStreak + " straight quarter(s)";
                parts.add(streakDesc + " (last " + eps.quartersConsidered + " reported)");
            } else {
                parts.add("EPS has landed in-line with estimates recently");
            }
        }
        if (parts.isEmpty()) {
            return "Not enough data to build a trend narrative.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            sb.append(capitalize(parts.get(i)));
            sb.append(i < parts.size() - 1 ? "; " : ".");
        }
        sb.append(" Trend-based read from the observed history — not an analyst consensus forecast.");
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
