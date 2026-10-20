package com.svp.tracker.finance.dto;

import java.util.List;

public record RhPredictNotesDeskDto(List<RhPredictNoteDto> notes, RhPredictFeedbackDto feedback) {}
