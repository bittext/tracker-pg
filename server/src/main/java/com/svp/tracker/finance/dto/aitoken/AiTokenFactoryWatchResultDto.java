package com.svp.tracker.finance.dto.aitoken;

import java.util.List;

public record AiTokenFactoryWatchResultDto(int addedOrUpdated, List<String> symbols) {}
