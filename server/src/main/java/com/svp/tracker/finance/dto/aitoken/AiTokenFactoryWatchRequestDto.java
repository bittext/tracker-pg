package com.svp.tracker.finance.dto.aitoken;

import java.util.List;

public record AiTokenFactoryWatchRequestDto(List<String> symbols, String thesisTag) {}
