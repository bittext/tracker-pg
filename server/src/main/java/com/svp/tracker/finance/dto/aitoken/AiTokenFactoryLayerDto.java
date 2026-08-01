package com.svp.tracker.finance.dto.aitoken;

import java.util.List;

public record AiTokenFactoryLayerDto(
        String id,
        String title,
        String subtitle,
        /** profit_pool | scarce | commoditized | demand | software */
        String economicsTag,
        Double layerAvgDayPercent,
        Double layerAvgYtdPercent,
        List<AiTokenFactoryCompanyDto> companies) {}
