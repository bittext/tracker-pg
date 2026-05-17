package com.svp.tracker.finance.dto;

/** HTML fragment from the optional robinhood-notebook-svc (nbconvert / papermill). */
public record RobinhoodNotebookRenderDto(int year, String html, String source, String note) {}
