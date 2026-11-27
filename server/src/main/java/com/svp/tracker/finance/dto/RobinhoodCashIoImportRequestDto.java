package com.svp.tracker.finance.dto;

/** A Robinhood CSV to preview or import into Inputs / Outputs for one of your accounts. */
public record RobinhoodCashIoImportRequestDto(String csv, String accountSuffix, String dateMode) {}
