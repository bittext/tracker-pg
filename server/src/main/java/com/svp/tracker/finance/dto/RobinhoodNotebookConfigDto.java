package com.svp.tracker.finance.dto;

/** UI hints for optional JupyterLab + notebook sidecar (see notebooks/robinhood). */
public record RobinhoodNotebookConfigDto(
        boolean jupyterLabConfigured,
        String jupyterLabUrl,
        boolean notebookServiceConfigured,
        String notebookServiceNote) {}
