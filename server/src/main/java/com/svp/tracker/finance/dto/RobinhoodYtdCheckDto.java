package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Individual ••••3370 broker YTD vs open mark. Informational, not a tax document. */
public record RobinhoodYtdCheckDto(
        String accountSuffix,
        String accountLabel,
        int year,
        LocalDate asOf,
        Instant fetchedAt,
        boolean live,
        String source,
        BigDecimal accountValue,
        BigDecimal equityValue,
        BigDecimal cash,
        BigDecimal buyingPower,
        BigDecimal realizedYtd,
        BigDecimal realizedEquity,
        BigDecimal realizedOption,
        BigDecimal realizedCrypto,
        BigDecimal realizedCalendarDay,
        BigDecimal realizedAppDay,
        int appDayTrades,
        BigDecimal openUnrealized,
        BigDecimal impliedYtdTotal,
        String note,
        List<RobinhoodYtdCheckPositionDto> positions,
        List<RobinhoodYtdCheckCloseDto> recentCloses,
        List<String> warnings) {}
