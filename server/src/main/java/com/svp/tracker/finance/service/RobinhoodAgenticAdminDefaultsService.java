package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.RobinhoodAgenticAdminDefaults;
import com.svp.tracker.finance.domain.RobinhoodAgenticSettings;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminDefaultsDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminDefaultsRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticAdminDefaultsRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSettingsRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RobinhoodAgenticAdminDefaultsService {

    private static final long SINGLETON_ID = 1L;

    private final RobinhoodAgenticAdminDefaultsRepository defaultsRepository;
    private final RobinhoodAgenticSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public RobinhoodAgenticAdminDefaultsDto getDefaults() {
        return toDto(requireRow());
    }

    @Transactional
    public RobinhoodAgenticAdminDefaultsDto saveDefaults(RobinhoodAgenticAdminDefaultsRequestDto req) {
        RobinhoodAgenticAdminDefaults row = requireRow();
        if (req.requireApproval() != null) {
            row.setRequireApproval(req.requireApproval());
        }
        row.setMaxOrderNotional(req.maxOrderNotional());
        row.setAllowedSymbols(normalizeSymbols(req.allowedSymbols()));
        if (req.autoTradeEnabled() != null) {
            row.setAutoTradeEnabled(req.autoTradeEnabled());
        }
        if (req.autoTradeKillSwitch() != null) {
            row.setAutoTradeKillSwitch(req.autoTradeKillSwitch());
        }
        if (req.autoTradeRequireApproval() != null) {
            row.setAutoTradeRequireApproval(req.autoTradeRequireApproval());
        }
        if (req.autoTradeMinPositivityBuy() != null) {
            row.setAutoTradeMinPositivityBuy(req.autoTradeMinPositivityBuy());
        }
        if (req.autoTradeMaxPositivitySell() != null) {
            row.setAutoTradeMaxPositivitySell(req.autoTradeMaxPositivitySell());
        }
        if (req.autoTradeMinSpikeZ() != null) {
            row.setAutoTradeMinSpikeZ(req.autoTradeMinSpikeZ());
        }
        if (req.autoTradeMinMentions24h() != null) {
            row.setAutoTradeMinMentions24h(Math.max(1, req.autoTradeMinMentions24h()));
        }
        if (req.autoTradeOrderQuantity() != null) {
            row.setAutoTradeOrderQuantity(req.autoTradeOrderQuantity());
        }
        if (req.autoTradeMaxTradesPerDay() != null) {
            row.setAutoTradeMaxTradesPerDay(Math.max(1, req.autoTradeMaxTradesPerDay()));
        }
        row.setAutoTradeMaxDailyNotional(req.autoTradeMaxDailyNotional());
        if (req.autoTradeCooldownMinutes() != null) {
            row.setAutoTradeCooldownMinutes(Math.max(1, req.autoTradeCooldownMinutes()));
        }
        if (req.autoTradeMarketHoursOnly() != null) {
            row.setAutoTradeMarketHoursOnly(req.autoTradeMarketHoursOnly());
        }
        if (req.approvalAlertEmailEnabled() != null) {
            row.setApprovalAlertEmailEnabled(req.approvalAlertEmailEnabled());
        }
        if (req.approvalAlertSmsEnabled() != null) {
            row.setApprovalAlertSmsEnabled(req.approvalAlertSmsEnabled());
        }
        row.setUpdatedAt(Instant.now());
        return toDto(defaultsRepository.save(row));
    }

    /** Template for users without a saved settings row. */
    @Transactional(readOnly = true)
    public RobinhoodAgenticSettings newUserSettingsTemplate() {
        return copyToSettings(requireRow(), new RobinhoodAgenticSettings());
    }

    @Transactional
    public void applyDefaultsToUser(long ownerUserId) {
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(() -> {
                    RobinhoodAgenticSettings s = new RobinhoodAgenticSettings();
                    s.setOwnerUserId(ownerUserId);
                    return s;
                });
        copyToSettings(requireRow(), row);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
    }

    @Transactional(readOnly = true)
    public boolean isApprovalAlertEmailEnabled() {
        return requireRow().isApprovalAlertEmailEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isApprovalAlertSmsEnabled() {
        return requireRow().isApprovalAlertSmsEnabled();
    }

    private RobinhoodAgenticAdminDefaults requireRow() {
        return defaultsRepository
                .findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("robinhood_agentic_admin_defaults row missing"));
    }

    private static RobinhoodAgenticSettings copyToSettings(
            RobinhoodAgenticAdminDefaults src, RobinhoodAgenticSettings dest) {
        dest.setRequireApproval(src.isRequireApproval());
        dest.setMaxOrderNotional(src.getMaxOrderNotional());
        dest.setAllowedSymbols(src.getAllowedSymbols());
        dest.setAutoTradeEnabled(src.isAutoTradeEnabled());
        dest.setAutoTradeKillSwitch(src.isAutoTradeKillSwitch());
        dest.setAutoTradeRequireApproval(src.isAutoTradeRequireApproval());
        dest.setAutoTradeMinPositivityBuy(src.getAutoTradeMinPositivityBuy());
        dest.setAutoTradeMaxPositivitySell(src.getAutoTradeMaxPositivitySell());
        dest.setAutoTradeMinSpikeZ(src.getAutoTradeMinSpikeZ());
        dest.setAutoTradeMinMentions24h(src.getAutoTradeMinMentions24h());
        dest.setAutoTradeOrderQuantity(src.getAutoTradeOrderQuantity());
        dest.setAutoTradeMaxTradesPerDay(src.getAutoTradeMaxTradesPerDay());
        dest.setAutoTradeMaxDailyNotional(src.getAutoTradeMaxDailyNotional());
        dest.setAutoTradeCooldownMinutes(src.getAutoTradeCooldownMinutes());
        dest.setAutoTradeMarketHoursOnly(src.isAutoTradeMarketHoursOnly());
        return dest;
    }

    private static RobinhoodAgenticAdminDefaultsDto toDto(RobinhoodAgenticAdminDefaults row) {
        return new RobinhoodAgenticAdminDefaultsDto(
                row.isRequireApproval(),
                row.getMaxOrderNotional(),
                row.getAllowedSymbols() == null ? "" : row.getAllowedSymbols(),
                row.isAutoTradeEnabled(),
                row.isAutoTradeKillSwitch(),
                row.isAutoTradeRequireApproval(),
                row.getAutoTradeMinPositivityBuy(),
                row.getAutoTradeMaxPositivitySell(),
                row.getAutoTradeMinSpikeZ(),
                row.getAutoTradeMinMentions24h(),
                row.getAutoTradeOrderQuantity(),
                row.getAutoTradeMaxTradesPerDay(),
                row.getAutoTradeMaxDailyNotional(),
                row.getAutoTradeCooldownMinutes(),
                row.isAutoTradeMarketHoursOnly(),
                row.isApprovalAlertEmailEnabled(),
                row.isApprovalAlertSmsEnabled(),
                row.getUpdatedAt());
    }

    private static String normalizeSymbols(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
