package com.svp.tracker.finance.dto;

import java.util.List;

public record FinanceInsuranceOptionsDto(
        List<FinanceInsuranceOptionDto> policyTypes, List<FinanceInsuranceOptionDto> premiumFrequencies) {}
