package com.svp.tracker.finance.service;

/** Sidecar or MCP returned HTTP 401 — caller may refresh OAuth tokens and retry. */
public class RobinhoodAgenticUnauthorizedException extends RuntimeException {
    public RobinhoodAgenticUnauthorizedException(String message) {
        super(message);
    }
}
