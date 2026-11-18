package com.svp.tracker.finance.dto;

/** Sets the note for one row. A blank {@code body} removes the note. */
public record RhPredictRowNoteRequest(String key, String body) {}
