package com.svp.tracker.finance.dto;

import java.util.List;

public record FinanceLoanOptionsDto(List<FinanceLoanOptionDto> loanNatures, List<FinanceLoanOptionDto> paymentFrequencies) {}
