package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceTaxDeskWorkbookDto(
        int taxYear,
        LocalDate asOf,
        String filingStatus,
        String residentState,
        String riskLevel,
        String riskHeadline,
        BigDecimal estimatedFederalTax,
        BigDecimal estimatedIncomeTax,
        BigDecimal estimatedNiit,
        BigDecimal projectedWithholding,
        BigDecimal estimatesPaid,
        BigDecimal totalCredits,
        BigDecimal filingDayBalance,
        BigDecimal targetRefund,
        BigDecimal recommendedAdditionalPrepay,
        BigDecimal requiredAnnualPayment,
        String rapBasis,
        BigDecimal priorYearSafeHarbor,
        BigDecimal currentYearSafeHarbor,
        BigDecimal penaltyExposure,
        boolean safeHarborCovered,
        boolean refundTargetCovered,
        BigDecimal wagesProjected,
        BigDecimal externalProjected,
        BigDecimal realizedYtd,
        BigDecimal capitalLossCarryoverApplied,
        BigDecimal netTaxableGain,
        BigDecimal agi,
        BigDecimal standardDeduction,
        BigDecimal taxableIncome,
        BigDecimal childCredit,
        BigDecimal vsPriorSnapshotTaxDelta,
        FinanceTaxDeskSettingsDto settings,
        List<FinanceTaxDeskIncomeItemDto> incomeItems,
        List<FinanceTaxDeskPaymentDto> payments,
        List<FinanceTaxDeskQuarterDto> quarters,
        FinanceTaxDeskTodayDto today,
        List<FinanceTaxDeskFilingRefDto> priorFilings,
        List<FinanceTaxDeskIrsSourceDto> irsSources,
        List<String> assumptions,
        List<String> caveats,
        String cpaNarrative,
        String realizedYtdSource,
        BigDecimal fifoTapeRealizedYtd,
        List<FinanceTaxDeskRhAccountRealizedDto> robinhoodRealizedAccounts) {

    public FinanceTaxDeskWorkbookDto {
        if (realizedYtdSource == null || realizedYtdSource.isBlank()) {
            realizedYtdSource = "FIFO_TAPE";
        }
        if (fifoTapeRealizedYtd == null) {
            fifoTapeRealizedYtd = realizedYtd;
        }
        if (robinhoodRealizedAccounts == null) {
            robinhoodRealizedAccounts = List.of();
        }
    }
}
