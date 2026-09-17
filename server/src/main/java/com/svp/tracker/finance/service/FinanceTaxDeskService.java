package com.svp.tracker.finance.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceTax1040Return;
import com.svp.tracker.finance.domain.FinanceTaxDeskDailySnapshot;
import com.svp.tracker.finance.domain.FinanceTaxDeskIncomeItem;
import com.svp.tracker.finance.domain.FinanceTaxDeskPayment;
import com.svp.tracker.finance.domain.FinanceTaxDeskSettings;
import com.svp.tracker.finance.dto.FinanceTaxDeskFilingRefDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskIncomeItemDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskIncomeItemWriteDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskIrsSourceDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskPageDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskPaymentDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskPaymentWriteDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskQuarterDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskRhAccountRealizedDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskSettingsDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskSettingsWriteDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskSnapshotSummaryDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskTodayDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskTradeRowDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskWorkbookDto;
import com.svp.tracker.finance.dto.RobinhoodExecutedTradeDto;
import com.svp.tracker.finance.dto.RobinhoodExecutedTradesDto;
import com.svp.tracker.finance.repository.FinanceTax1040ReturnRepository;
import com.svp.tracker.finance.repository.FinanceTaxDeskDailySnapshotRepository;
import com.svp.tracker.finance.repository.FinanceTaxDeskIncomeItemRepository;
import com.svp.tracker.finance.repository.FinanceTaxDeskPaymentRepository;
import com.svp.tracker.finance.repository.FinanceTaxDeskSettingsRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.tax.Form1040ParsedSummary;
import com.svp.tracker.finance.tax.Form1040TextParser;
import com.svp.tracker.finance.taxdesk.FederalTaxDeskCalculator;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementDueItem;
import com.svp.tracker.management.repository.ManagementDueItemRepository;
import com.svp.tracker.reportcal.domain.ReportCalendarEntry;
import com.svp.tracker.reportcal.repository.ReportCalendarEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceTaxDeskService {

    static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final BigDecimal USD_225814 = new BigDecimal("225814.00");
    private static final BigDecimal USD_22601 = new BigDecimal("22601.00");
    private static final BigDecimal USD_116898 = new BigDecimal("116898.00");
    private static final BigDecimal USD_9654 = new BigDecimal("9654.00");
    private static final BigDecimal USD_222814 = new BigDecimal("222814.00");
    private static final BigDecimal USD_29239 = new BigDecimal("29239.00");
    private static final BigDecimal USD_2700 = new BigDecimal("2700.00");
    private static final BigDecimal USD_5000 = new BigDecimal("5000.00");

    private final CurrentUserService currentUser;
    private final FinanceTaxDeskSettingsRepository settingsRepository;
    private final FinanceTaxDeskIncomeItemRepository incomeRepository;
    private final FinanceTaxDeskPaymentRepository paymentRepository;
    private final FinanceTaxDeskDailySnapshotRepository snapshotRepository;
    private final FinanceTax1040ReturnRepository tax1040Repository;
    private final ReportCalendarEntryRepository calendarRepository;
    private final ManagementDueItemRepository dueItemRepository;
    private final RobinhoodExecutedTradesService executedTradesService;
    private final RobinhoodBrokerRealizedPnlService brokerRealizedPnlService;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public FinanceTaxDeskPageDto load(int taxYear, LocalDate asOfOrNull, boolean persistToday) {
        long owner = currentUser.requireUserId();
        return loadForOwner(owner, taxYear, asOfOrNull, persistToday);
    }

    @Transactional
    public FinanceTaxDeskPageDto loadForOwner(long owner, int taxYear, LocalDate asOfOrNull, boolean persistToday) {
        if (taxYear < 2020 || taxYear > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxYear out of range");
        }
        LocalDate today = LocalDate.now(CENTRAL);
        LocalDate asOf = asOfOrNull == null ? today : asOfOrNull;
        seedDefaults(owner, taxYear);
        importIrsSources(owner, taxYear);

        boolean live = !asOf.isBefore(today);
        if (!live) {
            FinanceTaxDeskDailySnapshot stored =
                    snapshotRepository.findByOwnerUserIdAndTaxYearAndAsOfDate(owner, taxYear, asOf).orElse(null);
            if (stored != null) {
                FinanceTaxDeskWorkbookDto wb = readWorkbook(stored.getWorkbookJson());
                return new FinanceTaxDeskPageDto(taxYear, asOf, false, wb, history(owner, taxYear));
            }
        }

        FinanceTaxDeskWorkbookDto workbook = computeWorkbook(owner, taxYear, asOf, live);
        if (persistToday && (live || asOf.equals(today))) {
            saveSnapshot(owner, taxYear, asOf, workbook);
        }
        return new FinanceTaxDeskPageDto(taxYear, asOf, live, workbook, history(owner, taxYear));
    }

    @Transactional
    public FinanceTaxDeskPageDto saveSettings(int taxYear, FinanceTaxDeskSettingsWriteDto body) {
        long owner = currentUser.requireUserId();
        seedDefaults(owner, taxYear);
        FinanceTaxDeskSettings row = settingsRepository
                .findByOwnerUserIdAndTaxYear(owner, taxYear)
                .orElseThrow();
        Instant now = Instant.now();
        row.setFilingStatus(blankTo(body.filingStatus(), "MARRIED_FILING_JOINTLY"));
        row.setResidentState(blankTo(body.residentState(), "TX").toUpperCase(Locale.ROOT));
        row.setShortTermLossCarryover(nz(body.shortTermLossCarryover()));
        row.setLongTermLossCarryover(nz(body.longTermLossCarryover()));
        row.setPriorYearAgi(nz(body.priorYearAgi()));
        row.setPriorYearTax(nz(body.priorYearTax()));
        row.setChildTaxCredit(nz(body.childTaxCredit()));
        row.setTargetRefund(nz(body.targetRefund()));
        row.setNotes(body.notes() == null ? "" : body.notes().trim());
        row.setUpdatedAt(now);
        settingsRepository.save(row);
        return loadForOwner(owner, taxYear, null, true);
    }

    @Transactional
    public FinanceTaxDeskPageDto addIncome(int taxYear, FinanceTaxDeskIncomeItemWriteDto body) {
        long owner = currentUser.requireUserId();
        seedDefaults(owner, taxYear);
        FinanceTaxDeskIncomeItem row = new FinanceTaxDeskIncomeItem();
        row.setOwnerUserId(owner);
        row.setTaxYear(taxYear);
        applyIncome(row, body, incomeRepository.findByOwnerUserIdAndTaxYearOrderBySortOrderAscIdAsc(owner, taxYear).size());
        incomeRepository.save(row);
        return loadForOwner(owner, taxYear, null, true);
    }

    @Transactional
    public FinanceTaxDeskPageDto updateIncome(int taxYear, long id, FinanceTaxDeskIncomeItemWriteDto body) {
        long owner = currentUser.requireUserId();
        FinanceTaxDeskIncomeItem row = incomeRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Income item not found"));
        if (row.getTaxYear() != taxYear) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year mismatch");
        }
        applyIncome(row, body, row.getSortOrder());
        incomeRepository.save(row);
        return loadForOwner(owner, taxYear, null, true);
    }

    @Transactional
    public FinanceTaxDeskPageDto deleteIncome(int taxYear, long id) {
        long owner = currentUser.requireUserId();
        FinanceTaxDeskIncomeItem row = incomeRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Income item not found"));
        incomeRepository.delete(row);
        return loadForOwner(owner, taxYear, null, true);
    }

    @Transactional
    public FinanceTaxDeskPageDto addPayment(int taxYear, FinanceTaxDeskPaymentWriteDto body) {
        long owner = currentUser.requireUserId();
        seedDefaults(owner, taxYear);
        if (body.paidOn() == null || body.amount() == null || body.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paidOn and a positive amount are required");
        }
        FinanceTaxDeskPayment row = new FinanceTaxDeskPayment();
        row.setOwnerUserId(owner);
        row.setTaxYear(taxYear);
        row.setPaidOn(body.paidOn());
        row.setAmount(body.amount().setScale(2, RoundingMode.HALF_UP));
        row.setMethod(body.method() == null ? "" : body.method().trim());
        row.setSource(blankTo(body.source(), "MANUAL").toUpperCase(Locale.ROOT));
        row.setSourceRef("manual:" + Instant.now().toEpochMilli());
        row.setNotes(body.notes() == null ? "" : body.notes().trim());
        row.setCreatedAt(Instant.now());
        paymentRepository.save(row);
        return loadForOwner(owner, taxYear, null, true);
    }

    @Transactional
    public FinanceTaxDeskPageDto deletePayment(int taxYear, long id) {
        long owner = currentUser.requireUserId();
        FinanceTaxDeskPayment row = paymentRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        String source = row.getSource() == null ? "" : row.getSource().trim().toUpperCase(Locale.ROOT);
        if ("CALENDAR".equals(source) || "DUE".equals(source)) {
            // Hard-delete would be re-imported on the next load from the same Life calendar/Due line.
            row.setIgnored(true);
            paymentRepository.save(row);
        } else {
            paymentRepository.delete(row);
        }
        return loadForOwner(owner, taxYear, null, true);
    }

    public List<Long> snapshotOwnerIds() {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(settingsRepository.findDistinctOwnerUserIds());
        ids.addAll(snapshotRepository.findDistinctOwnerUserIds());
        connectionRepository.findAll().forEach(c -> ids.add(c.getOwnerUserId()));
        return List.copyOf(ids);
    }

    private FinanceTaxDeskWorkbookDto computeWorkbook(long owner, int taxYear, LocalDate asOf, boolean live) {
        FinanceTaxDeskSettings settings = settingsRepository
                .findByOwnerUserIdAndTaxYear(owner, taxYear)
                .orElseThrow();
        List<FinanceTaxDeskIncomeItem> income =
                incomeRepository.findByOwnerUserIdAndTaxYearOrderBySortOrderAscIdAsc(owner, taxYear);
        List<FinanceTaxDeskPayment> payments =
                paymentRepository.findByOwnerUserIdAndTaxYearAndIgnoredFalseOrderByPaidOnAscIdAsc(owner, taxYear);

        BigDecimal wages = BigDecimal.ZERO;
        BigDecimal external = BigDecimal.ZERO;
        BigDecimal withholding = BigDecimal.ZERO;
        for (FinanceTaxDeskIncomeItem item : income) {
            BigDecimal annual = FederalTaxDeskCalculator.projectedAmount(
                    item.getYtdAmount(), item.getAnnualProjected(), asOf, taxYear);
            BigDecimal wh = FederalTaxDeskCalculator.projectedAmount(
                    item.getWithholdingYtd(), item.getWithholdingAnnualProjected(), asOf, taxYear);
            withholding = withholding.add(wh);
            if ("EXTERNAL".equalsIgnoreCase(item.getKind()) || "OTHER".equalsIgnoreCase(item.getKind())) {
                external = external.add(annual);
            } else {
                wages = wages.add(annual);
            }
        }

        RobinhoodExecutedTradesDto tradesDto = executedTradesService.buildForOwner(owner, taxYear);
        BigDecimal realizedYtd = BigDecimal.ZERO;
        List<FinanceTaxDeskTradeRowDto> todayTrades = new ArrayList<>();
        BigDecimal realizedToday = BigDecimal.ZERO;
        BigDecimal buyNotional = BigDecimal.ZERO;
        BigDecimal sellNotional = BigDecimal.ZERO;
        int sellCount = 0;
        for (RobinhoodExecutedTradeDto t : tradesDto.trades()) {
            LocalDate d = tradeDate(t.executedAt());
            if (d == null || d.isAfter(asOf) || d.isBefore(LocalDate.of(taxYear, 1, 1)) || d.isAfter(LocalDate.of(taxYear, 12, 31))) {
                continue;
            }
            if (t.realizedPnl() != null && d.getYear() == taxYear) {
                realizedYtd = realizedYtd.add(t.realizedPnl());
            }
            if (d.equals(asOf)) {
                todayTrades.add(new FinanceTaxDeskTradeRowDto(
                        t.symbol(),
                        t.side(),
                        t.quantity(),
                        t.averagePrice(),
                        t.notional(),
                        t.realizedPnl(),
                        t.accountLabel(),
                        t.executedAt()));
                if (t.realizedPnl() != null) {
                    realizedToday = realizedToday.add(t.realizedPnl());
                }
                if (isSellSide(t.side())) {
                    sellCount++;
                    if (t.notional() != null) {
                        sellNotional = sellNotional.add(t.notional());
                    }
                } else if (isBuySide(t.side()) && t.notional() != null) {
                    buyNotional = buyNotional.add(t.notional());
                }
            }
        }
        realizedYtd = realizedYtd.setScale(2, RoundingMode.HALF_UP);
        realizedToday = realizedToday.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fifoTapeRealizedYtd = realizedYtd;
        String realizedSource = "FIFO_TAPE";
        List<FinanceTaxDeskRhAccountRealizedDto> rhAccounts = List.of();
        Optional<RobinhoodBrokerRealizedPnlService.Fetched> broker = Optional.empty();
        if (live) {
            broker = brokerRealizedPnlService.fetchLive(owner, taxYear, asOf);
            broker.ifPresent(fetched -> brokerRealizedPnlService.persist(owner, taxYear, fetched));
        }
        if (broker.isEmpty()) {
            broker = brokerRealizedPnlService.storedOnOrBefore(owner, taxYear, asOf);
        }
        if (broker.isPresent()) {
            realizedYtd = broker.get().total();
            realizedSource = "ROBINHOOD";
            rhAccounts = broker.get().accounts();
        }

        List<FederalTaxDeskCalculator.Payment> calcPays = payments.stream()
                .map(p -> new FederalTaxDeskCalculator.Payment(p.getPaidOn(), p.getAmount()))
                .toList();
        FederalTaxDeskCalculator.Result calc = FederalTaxDeskCalculator.compute(new FederalTaxDeskCalculator.Input(
                settings.getFilingStatus(),
                wages,
                external,
                realizedYtd,
                settings.getShortTermLossCarryover(),
                settings.getLongTermLossCarryover(),
                settings.getChildTaxCredit(),
                withholding,
                calcPays,
                settings.getPriorYearAgi(),
                settings.getPriorYearTax(),
                settings.getTargetRefund(),
                asOf,
                taxYear));

        BigDecimal priorDelta = BigDecimal.ZERO;
        List<FinanceTaxDeskDailySnapshot> snaps =
                snapshotRepository.findByOwnerUserIdAndTaxYearOrderByAsOfDateDesc(owner, taxYear);
        FinanceTaxDeskDailySnapshot previous = snaps.stream()
                .filter(s -> s.getAsOfDate().isBefore(asOf))
                .findFirst()
                .orElse(null);
        if (previous != null) {
            priorDelta = calc.federalTax().subtract(nz(previous.getEstimatedTax())).setScale(2, RoundingMode.HALF_UP);
        }

        String riskLevel = calc.riskLevel();
        if (priorDelta.compareTo(new BigDecimal("2000")) >= 0 && !"PENALTY_RISK".equals(riskLevel)) {
            riskLevel = "LIABILITY_SPIKE";
        }

        List<FinanceTaxDeskFilingRefDto> filings = priorFilings(owner);
        List<FinanceTaxDeskIrsSourceDto> sources = irsSources(owner, taxYear);

        List<String> assumptions = List.of(
                "Filing status " + humanStatus(settings.getFilingStatus()) + "; residence " + settings.getResidentState()
                        + " (Texas has no individual income tax).",
                "W-2 and external rows are your inputs. Annual projection is used when set; otherwise year-to-date is annualized.",
                "ROBINHOOD".equals(realizedSource)
                        ? "Robinhood calendar YTD realized (broker) on Individual, Agentic, and Ammu from 1 January "
                                + taxYear + " through " + asOf.format(MDY)
                                + " is used for the tax estimate. App FIFO tape is a footnote only."
                        : "Robinhood broker YTD was unavailable, so realized P&L is app FIFO from executed trades in Individual, Agentic, and Ammu through "
                                + asOf.format(MDY) + ". Unmatched sells contribute $0 on that tape.",
                "Trading gain is treated as short-term (ordinary brackets), matching active HOOD / MRVL / MRNA / option turnover.",
                "2025 capital-loss carryover is applied before any 2026 gain is taxed. Unused 2025 bracket space does not carry.",
                "NIIT is 3.8% of the lesser of net investment income or MAGI over $250,000 MFJ.",
                "Withholding is treated as paid ratably across the four 1040-ES dates (Form 2210 regular method).",
                "Calendar / Due IRS lines are imported as estimated-tax credits only when a dollar amount can be read.",
                "Target refund is extra prepayment so Form 1040 shows a refund rather than a balance due.");

        List<String> caveats = List.of(
                "These are estimated-tax working papers, not a CPA attest opinion, Form 1040, Form 2210, or e-file.",
                "The IRS will use Forms W-2, 1099-B (with basis and wash-sale adjustments), 1099-NEC/INT/DIV, and the return as filed.",
                "Robinhood YTD realized is a broker screen figure, not Form 1099-B. Wash sales, cost-basis adjustments, and year-end 1099-B can differ.",
                "The in-app 1040 PDF extract has misread line numbers as dollars on at least one upload; 2025 figures here were seeded from the reviewed return, not that extract.",
                "More trades after " + asOf.format(MDY) + " change tax. Selling open MRNA (unrealized) is not in this number until closed.",
                "Annualized-income installment method (Form 2210 AI) is not computed; it can reduce penalty when income is back-loaded.",
                "Penalty exposure uses a 7% annualized underpayment rate as a planning flag, not IRS interest compounding.",
                "Q4 1040-ES is due 15 January " + (taxYear + 1) + ". The return is due mid-April " + (taxYear + 1) + ".");

        String narrative = buildNarrative(
                settings, calc, asOf, taxYear, riskLevel, priorDelta, filings, sources, realizedSource);

        FinanceTaxDeskTodayDto today = new FinanceTaxDeskTodayDto(
                asOf,
                todayTrades.size(),
                sellCount,
                realizedToday,
                buyNotional.setScale(2, RoundingMode.HALF_UP),
                sellNotional.setScale(2, RoundingMode.HALF_UP),
                todayTrades);

        return new FinanceTaxDeskWorkbookDto(
                taxYear,
                asOf,
                settings.getFilingStatus(),
                settings.getResidentState(),
                riskLevel,
                "LIABILITY_SPIKE".equals(riskLevel)
                        ? "Estimated federal tax rose "
                                + money(priorDelta)
                                + " versus the prior saved day. Review whether another estimate or extra withholding is needed."
                        : calc.riskHeadline(),
                calc.federalTax(),
                calc.incomeTax(),
                calc.niit(),
                calc.withholding(),
                calc.estimatesPaid(),
                calc.totalCredits(),
                calc.filingBalance(),
                calc.targetRefund(),
                calc.additionalPrepay(),
                calc.requiredAnnualPayment(),
                calc.rapBasis(),
                calc.priorYearSafeHarbor(),
                calc.currentYearSafeHarbor(),
                calc.penaltyExposure(),
                calc.safeHarborCovered(),
                calc.refundTargetCovered(),
                calc.wages(),
                calc.otherOrdinary(),
                realizedYtd,
                calc.carryoverApplied(),
                calc.netTaxableGain(),
                calc.agi(),
                calc.standardDeduction(),
                calc.taxableIncome(),
                calc.childCredit(),
                priorDelta,
                toSettingsDto(settings),
                income.stream().map(this::toIncomeDto).toList(),
                payments.stream().map(this::toPaymentDto).toList(),
                calc.quarters().stream().map(this::toQuarterDto).toList(),
                today,
                filings,
                sources,
                assumptions,
                caveats,
                narrative,
                realizedSource,
                fifoTapeRealizedYtd,
                rhAccounts);
    }

    private String buildNarrative(
            FinanceTaxDeskSettings settings,
            FederalTaxDeskCalculator.Result calc,
            LocalDate asOf,
            int taxYear,
            String riskLevel,
            BigDecimal priorDelta,
            List<FinanceTaxDeskFilingRefDto> filings,
            List<FinanceTaxDeskIrsSourceDto> sources,
            String realizedSource) {
        String filingDay = calc.filingBalance().signum() > 0
                ? "a balance due of " + money(calc.filingBalance())
                : calc.filingBalance().signum() < 0
                        ? "a refund of " + money(calc.filingBalance().abs())
                        : "a zero balance";
        StringBuilder sb = new StringBuilder();
        sb.append("ESTIMATED-TAX WORKING PAPERS — TAX YEAR ")
                .append(taxYear)
                .append('\n')
                .append("Prepared ")
                .append(asOf.format(MDY))
                .append(" (America/Chicago) in the style of a CPA estimated-tax file. ")
                .append("This is a planning memorandum, not an attest report and not tax advice.\n\n");
        sb.append("1. Objective\n");
        sb.append("Keep 1040-ES deposits on time so Form 2210 underpayment penalty stays off, and prepay enough ")
                .append("that the ")
                .append(taxYear + 1)
                .append(" filing produces a refund of about ")
                .append(money(calc.targetRefund()))
                .append(" rather than a check to the IRS.\n\n");
        sb.append("2. Facts relied on\n");
        sb.append("Status: ")
                .append(humanStatus(settings.getFilingStatus()))
                .append(". Residence: ")
                .append(settings.getResidentState())
                .append(". W-2 / external projection ")
                .append(money(calc.wages().add(calc.otherOrdinary())))
                .append(" with projected withholding ")
                .append(money(calc.withholding()))
                .append(". ")
                .append("ROBINHOOD".equals(realizedSource)
                        ? "Robinhood calendar YTD realized (broker) "
                        : "App FIFO realized tape ")
                .append(money(calc.realizedCapital()))
                .append(" after applying capital-loss carryover ")
                .append(money(calc.carryoverApplied()))
                .append(" leaves taxable gain ")
                .append(money(calc.netTaxableGain()))
                .append(". Estimated payments recorded: ")
                .append(money(calc.estimatesPaid()))
                .append(".\n");
        if (!filings.isEmpty()) {
            sb.append("Filed-return PDFs in the app: ");
            for (int i = 0; i < filings.size(); i++) {
                FinanceTaxDeskFilingRefDto f = filings.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(f.taxYear()).append(" (").append(f.originalFilename()).append(")");
                if (f.parserUnreliable()) {
                    sb.append(" — extract unreliable, use reviewed figures");
                }
            }
            sb.append(".\n");
        }
        if (!sources.isEmpty()) {
            sb.append("Life → Management calendar / Due IRS items: ");
            for (int i = 0; i < sources.size(); i++) {
                FinanceTaxDeskIrsSourceDto s = sources.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(s.kind())
                        .append(" ")
                        .append(s.title());
                if (s.date() != null) {
                    sb.append(" ").append(s.date());
                }
                if (s.amount() != null) {
                    sb.append(" ").append(money(s.amount()));
                }
            }
            sb.append(".\n");
        }
        sb.append('\n');
        sb.append("3. Computation (2026 MFJ ordinary brackets; NIIT 3.8%)\n");
        sb.append("AGI ")
                .append(money(calc.agi()))
                .append(" − standard deduction ")
                .append(money(calc.standardDeduction()))
                .append(" = taxable income ")
                .append(money(calc.taxableIncome()))
                .append(". Income tax ")
                .append(money(calc.incomeTax()))
                .append(" − child/other dependent credit ")
                .append(money(calc.childCredit()))
                .append(" = ")
                .append(money(calc.taxAfterCredits()))
                .append(". NIIT ")
                .append(money(calc.niit()))
                .append(". Combined federal tax ")
                .append(money(calc.federalTax()))
                .append(".\n\n");
        sb.append("4. Credits and filing-day result\n");
        sb.append("Projected withholding ")
                .append(money(calc.withholding()))
                .append(" + 1040-ES payments ")
                .append(money(calc.estimatesPaid()))
                .append(" = ")
                .append(money(calc.totalCredits()))
                .append(". Filing day currently projects ")
                .append(filingDay)
                .append(". Additional prepay to hit the refund target: ")
                .append(money(calc.additionalPrepay()))
                .append(".\n\n");
        sb.append("5. Estimated-tax calendar and penalty\n");
        sb.append("Required annual payment is ")
                .append(money(calc.requiredAnnualPayment()))
                .append(" (")
                .append(calc.rapBasis())
                .append("). Safe harbor covered: ")
                .append(calc.safeHarborCovered() ? "yes" : "no")
                .append(". Approximate underpayment exposure: ")
                .append(money(calc.penaltyExposure()))
                .append(". Risk band: ")
                .append(riskLevel.replace('_', ' '))
                .append(" — ")
                .append(calc.riskHeadline())
                .append('\n');
        for (FederalTaxDeskCalculator.Quarter q : calc.quarters()) {
            sb.append("  Q")
                    .append(q.quarter())
                    .append(" due ")
                    .append(q.dueDate().format(MDY))
                    .append(" — required installment ")
                    .append(money(q.requiredInstallment()))
                    .append(", estimates in window ")
                    .append(money(q.estimateCredit()))
                    .append(", shortfall ")
                    .append(money(q.shortfall()))
                    .append(", pay this quarter (refund plan) ")
                    .append(money(q.suggestedPayment()))
                    .append(" [")
                    .append(q.status())
                    .append("].\n");
        }
        if (priorDelta.signum() != 0) {
            sb.append("\n6. Daily rollforward\nEstimated tax changed ")
                    .append(money(priorDelta))
                    .append(" versus the prior saved working papers.\n");
        }
        sb.append("\nPrepared as internal working papers. Recalculate from year-end information returns before filing.");
        return sb.toString();
    }

    private void seedDefaults(long owner, int taxYear) {
        Instant now = Instant.now();
        FinanceTaxDeskSettings settings = settingsRepository.findByOwnerUserIdAndTaxYear(owner, taxYear).orElse(null);
        if (settings == null) {
            settings = new FinanceTaxDeskSettings();
            settings.setOwnerUserId(owner);
            settings.setTaxYear(taxYear);
            settings.setFilingStatus("MARRIED_FILING_JOINTLY");
            settings.setResidentState("TX");
            settings.setShortTermLossCarryover(USD_116898);
            settings.setLongTermLossCarryover(USD_9654);
            settings.setPriorYearAgi(USD_222814);
            settings.setPriorYearTax(USD_29239);
            settings.setChildTaxCredit(USD_2700);
            settings.setTargetRefund(USD_5000);
            settings.setNotes(
                    "Seeded from the reviewed 2025 Form 1040 (MFJ, McKinney TX): W-2 $225,814, tax $29,239, "
                            + "capital-loss carryover $126,552 ($116,898 ST / $9,654 LT), child/other dependent credit $2,700. "
                            + "Target a filing-day refund so April is a repayment from IRS, not a payable.");
            settings.setCreatedAt(now);
            settings.setUpdatedAt(now);
            settingsRepository.save(settings);
        }
        List<FinanceTaxDeskIncomeItem> income =
                incomeRepository.findByOwnerUserIdAndTaxYearOrderBySortOrderAscIdAsc(owner, taxYear);
        if (income.isEmpty()) {
            FinanceTaxDeskIncomeItem w2 = new FinanceTaxDeskIncomeItem();
            w2.setOwnerUserId(owner);
            w2.setTaxYear(taxYear);
            w2.setKind("W2");
            w2.setPayer("W-2 wages (Evernorth + spouse, 2025 pattern until paystubs updated)");
            w2.setYtdAmount(BigDecimal.ZERO);
            w2.setAnnualProjected(USD_225814);
            w2.setWithholdingYtd(BigDecimal.ZERO);
            w2.setWithholdingAnnualProjected(USD_22601);
            w2.setNotes(
                    "Replace annual projection with 2026 W-2 box 1 / box 2 once known. YTD can be filled from paystubs; "
                            + "leave annual at the full-year estimate so the 1040-ES plan does not understate wages.");
            w2.setSortOrder(0);
            w2.setCreatedAt(now);
            w2.setUpdatedAt(now);
            incomeRepository.save(w2);
        }
    }

    private void dismissMisimportedCashMoves(long owner, int taxYear) {
        for (FinanceTaxDeskPayment p :
                paymentRepository.findByOwnerUserIdAndTaxYearAndIgnoredFalseOrderByPaidOnAscIdAsc(owner, taxYear)) {
            String source = p.getSource() == null ? "" : p.getSource().trim().toUpperCase(Locale.ROOT);
            if (!"CALENDAR".equals(source) && !"DUE".equals(source)) {
                continue;
            }
            if (FederalTaxDeskCalculator.looksLikeInternalCashMove(p.getNotes(), p.getMethod())) {
                p.setIgnored(true);
                paymentRepository.save(p);
            }
        }
    }

    private void importIrsSources(long owner, int taxYear) {
        dismissMisimportedCashMoves(owner, taxYear);
        LocalDate from = LocalDate.of(taxYear, 1, 1);
        LocalDate to = LocalDate.of(taxYear + 1, 1, 31);
        for (ReportCalendarEntry e :
                calendarRepository.findByOwnerUserIdAndEntryDateBetweenOrderByEntryDateAscCalendarTypeAscIdAsc(
                        owner, from, to)) {
            if (!FederalTaxDeskCalculator.looksLikeIrs(e.getTitle(), e.getBody(), e.getDetails())) {
                continue;
            }
            BigDecimal amt = FederalTaxDeskCalculator.parseMoney(e.getTitle(), e.getBody(), e.getDetails());
            if (amt == null || amt.signum() <= 0) {
                continue;
            }
            String ref = "calendar:" + e.getId();
            if (paymentRepository.existsByOwnerUserIdAndTaxYearAndSourceAndSourceRef(owner, taxYear, "CALENDAR", ref)) {
                continue;
            }
            FinanceTaxDeskPayment p = new FinanceTaxDeskPayment();
            p.setOwnerUserId(owner);
            p.setTaxYear(taxYear);
            p.setPaidOn(e.getEntryDate());
            p.setAmount(amt.setScale(2, RoundingMode.HALF_UP));
            p.setMethod("Calendar");
            p.setSource("CALENDAR");
            p.setSourceRef(ref);
            p.setNotes(firstLine(e.getTitle(), e.getBody()));
            p.setCreatedAt(Instant.now());
            paymentRepository.save(p);
        }
    }

    private List<FinanceTaxDeskIrsSourceDto> irsSources(long owner, int taxYear) {
        List<FinanceTaxDeskIrsSourceDto> out = new ArrayList<>();
        LocalDate from = LocalDate.of(taxYear, 1, 1);
        LocalDate to = LocalDate.of(taxYear + 1, 1, 31);
        for (ReportCalendarEntry e :
                calendarRepository.findByOwnerUserIdAndEntryDateBetweenOrderByEntryDateAscCalendarTypeAscIdAsc(
                        owner, from, to)) {
            if (!FederalTaxDeskCalculator.looksLikeIrs(e.getTitle(), e.getBody(), e.getDetails())) {
                continue;
            }
            out.add(new FinanceTaxDeskIrsSourceDto(
                    "CALENDAR",
                    e.getTitle(),
                    e.getEntryDate(),
                    FederalTaxDeskCalculator.parseMoney(e.getTitle(), e.getBody(), e.getDetails()),
                    firstLine(e.getBody(), e.getDetails()),
                    "calendar:" + e.getId()));
        }
        for (ManagementDueItem item : dueItemRepository.findByOwnerUserIdAndActiveTrueOrderByCounterpartyAscIdAsc(owner)) {
            if (!FederalTaxDeskCalculator.looksLikeIrs(item.getCounterparty(), item.getNotes())) {
                continue;
            }
            out.add(new FinanceTaxDeskIrsSourceDto(
                    "DUE",
                    item.getCounterparty(),
                    item.getOneOffDate(),
                    item.getAmountOverride(),
                    dueNote(item),
                    "due:" + item.getId()));
        }
        out.sort(Comparator.comparing(FinanceTaxDeskIrsSourceDto::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.title() == null ? "" : s.title()));
        return out;
    }

    private List<FinanceTaxDeskFilingRefDto> priorFilings(long owner) {
        List<FinanceTaxDeskFilingRefDto> out = new ArrayList<>();
        for (FinanceTax1040Return r : tax1040Repository.findByOwnerUserIdOrderByTaxYearDesc(owner)) {
            Form1040ParsedSummary s = summaryOf(r);
            boolean unreliable = unreliable(s);
            out.add(new FinanceTaxDeskFilingRefDto(
                    r.getId(),
                    r.getTaxYear(),
                    r.getOriginalFilename(),
                    s.getFilingStatus(),
                    s.getConfidenceLabel(),
                    s.getWagesSalariesTips(),
                    s.getTotalTaxAfterCredits() != null ? s.getTotalTaxAfterCredits() : s.getTotalTax(),
                    s.getFederalIncomeTaxWithheld(),
                    s.getEstimatedTaxPayments(),
                    s.getRefund(),
                    s.getAmountOwed(),
                    unreliable,
                    unreliable
                            ? "PDF extract looks like form line numbers, not dollars. Use the reviewed 2025 figures in Settings."
                            : s.getParseNote()));
        }
        return out;
    }

    private boolean unreliable(Form1040ParsedSummary s) {
        if (s == null) {
            return true;
        }
        if ("LOW".equalsIgnoreCase(s.getConfidenceLabel())) {
            return true;
        }
        BigDecimal wages = s.getWagesSalariesTips();
        return wages != null && wages.compareTo(new BigDecimal("1000")) < 0 && wages.signum() > 0;
    }

    private Form1040ParsedSummary summaryOf(FinanceTax1040Return r) {
        String t = r.getExtractedText();
        if (t != null && t.length() > 40 && !t.startsWith("(Could not read PDF text:")) {
            return Form1040TextParser.parse(t);
        }
        try {
            return jsonMapper.readValue(r.getSummaryJson(), Form1040ParsedSummary.class);
        } catch (JacksonException e) {
            return new Form1040ParsedSummary();
        }
    }

    private void saveSnapshot(long owner, int taxYear, LocalDate asOf, FinanceTaxDeskWorkbookDto workbook) {
        Instant now = Instant.now();
        FinanceTaxDeskDailySnapshot row = snapshotRepository
                .findByOwnerUserIdAndTaxYearAndAsOfDate(owner, taxYear, asOf)
                .orElseGet(FinanceTaxDeskDailySnapshot::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setTaxYear(taxYear);
            row.setAsOfDate(asOf);
            row.setCreatedAt(now);
        }
        row.setRiskLevel(workbook.riskLevel());
        row.setEstimatedTax(nz(workbook.estimatedFederalTax()));
        row.setFilingBalance(nz(workbook.filingDayBalance()));
        row.setPenaltyExposure(nz(workbook.penaltyExposure()));
        row.setRealizedYtd(nz(workbook.realizedYtd()));
        row.setWorkbookJson(writeWorkbook(workbook));
        row.setUpdatedAt(now);
        snapshotRepository.save(row);
    }

    private List<FinanceTaxDeskSnapshotSummaryDto> history(long owner, int taxYear) {
        return snapshotRepository.findByOwnerUserIdAndTaxYearOrderByAsOfDateDesc(owner, taxYear).stream()
                .map(s -> new FinanceTaxDeskSnapshotSummaryDto(
                        s.getAsOfDate(),
                        s.getRiskLevel(),
                        s.getEstimatedTax(),
                        s.getFilingBalance(),
                        s.getPenaltyExposure(),
                        s.getRealizedYtd()))
                .toList();
    }

    private void applyIncome(FinanceTaxDeskIncomeItem row, FinanceTaxDeskIncomeItemWriteDto body, int fallbackSort) {
        String kind = blankTo(body.kind(), "EXTERNAL").toUpperCase(Locale.ROOT);
        if (!Set.of("W2", "EXTERNAL", "OTHER").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be W2, EXTERNAL, or OTHER");
        }
        row.setKind(kind);
        row.setPayer(body.payer() == null ? "" : body.payer().trim());
        row.setYtdAmount(nz(body.ytdAmount()));
        row.setAnnualProjected(nz(body.annualProjected()));
        row.setWithholdingYtd(nz(body.withholdingYtd()));
        row.setWithholdingAnnualProjected(nz(body.withholdingAnnualProjected()));
        row.setNotes(body.notes() == null ? "" : body.notes().trim());
        row.setSortOrder(body.sortOrder() == null ? fallbackSort : body.sortOrder());
        row.setUpdatedAt(Instant.now());
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(row.getUpdatedAt());
        }
    }

    private FinanceTaxDeskSettingsDto toSettingsDto(FinanceTaxDeskSettings s) {
        return new FinanceTaxDeskSettingsDto(
                s.getId(),
                s.getTaxYear(),
                s.getFilingStatus(),
                s.getResidentState(),
                s.getShortTermLossCarryover(),
                s.getLongTermLossCarryover(),
                s.getPriorYearAgi(),
                s.getPriorYearTax(),
                s.getChildTaxCredit(),
                s.getTargetRefund(),
                s.getNotes());
    }

    private FinanceTaxDeskIncomeItemDto toIncomeDto(FinanceTaxDeskIncomeItem i) {
        return new FinanceTaxDeskIncomeItemDto(
                i.getId(),
                i.getKind(),
                i.getPayer(),
                i.getYtdAmount(),
                i.getAnnualProjected(),
                i.getWithholdingYtd(),
                i.getWithholdingAnnualProjected(),
                i.getNotes(),
                i.getSortOrder());
    }

    private FinanceTaxDeskPaymentDto toPaymentDto(FinanceTaxDeskPayment p) {
        return new FinanceTaxDeskPaymentDto(
                p.getId(),
                p.getPaidOn(),
                p.getAmount(),
                p.getMethod(),
                p.getSource(),
                p.getSourceRef(),
                p.getNotes());
    }

    private FinanceTaxDeskQuarterDto toQuarterDto(FederalTaxDeskCalculator.Quarter q) {
        return new FinanceTaxDeskQuarterDto(
                q.quarter(),
                q.dueDate(),
                q.requiredInstallment(),
                q.requiredToDate(),
                q.withholdingCredit(),
                q.estimateCredit(),
                q.paidToDate(),
                q.shortfall(),
                q.suggestedPayment(),
                q.status());
    }

    private FinanceTaxDeskWorkbookDto readWorkbook(String json) {
        try {
            return jsonMapper.readValue(json, FinanceTaxDeskWorkbookDto.class);
        } catch (JacksonException first) {
            try {
                var node = jsonMapper.readTree(json);
                if (node instanceof tools.jackson.databind.node.ObjectNode obj) {
                    if (!obj.has("realizedYtdSource")) {
                        obj.put("realizedYtdSource", "FIFO_TAPE");
                    }
                    if (!obj.has("fifoTapeRealizedYtd") && obj.has("realizedYtd")) {
                        obj.set("fifoTapeRealizedYtd", obj.get("realizedYtd"));
                    }
                    if (!obj.has("robinhoodRealizedAccounts")) {
                        obj.set("robinhoodRealizedAccounts", jsonMapper.createArrayNode());
                    }
                    return jsonMapper.treeToValue(obj, FinanceTaxDeskWorkbookDto.class);
                }
            } catch (Exception ignored) {
                // fall through to original error
            }
            throw new IllegalStateException("Could not read saved tax-desk snapshot", first);
        }
    }

    private String writeWorkbook(FinanceTaxDeskWorkbookDto wb) {
        try {
            return jsonMapper.writeValueAsString(wb);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDate tradeDate(Instant executedAt) {
        if (executedAt == null) {
            return null;
        }
        return executedAt.atZone(CENTRAL).toLocalDate();
    }

    static boolean isBuySide(String side) {
        String s = normalizeSide(side);
        return s.equals("buy") || s.startsWith("buy");
    }

    static boolean isSellSide(String side) {
        String s = normalizeSide(side);
        return s.equals("sell") || s.startsWith("sell");
    }

    private static String normalizeSide(String side) {
        if (side == null) {
            return "";
        }
        return side.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    private static String dueNote(ManagementDueItem item) {
        String rec = item.isRecurring()
                ? "Recurring on day " + item.getDayOfMonth() + " — IRS 1040-ES is quarterly; do not treat as 12 monthly IRS bills."
                : "One-off";
        String notes = item.getNotes() == null ? "" : item.getNotes().trim();
        return notes.isBlank() ? rec : rec + " " + notes;
    }

    private static String firstLine(String... parts) {
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim().replace('\n', ' ');
            }
        }
        return "";
    }

    private static String humanStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Married filing jointly";
        }
        return status.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String money(BigDecimal v) {
        BigDecimal n = nz(v);
        return "$" + String.format(Locale.US, "%,.2f", n);
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
