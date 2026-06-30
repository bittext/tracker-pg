package com.svp.tracker.finance.service;

import java.net.ConnectException;
import java.net.UnknownHostException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class RobinhoodAgenticSidecarErrors {

    private RobinhoodAgenticSidecarErrors() {}

    static boolean isUnreachable(Throwable e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof UnknownHostException || cursor instanceof ConnectException) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("unreachable")
                        || lower.contains("connection refused")
                        || lower.contains("unknown host")
                        || lower.contains("failed to connect")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    static boolean isSidecarDown(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return status == HttpStatus.BAD_GATEWAY || status == HttpStatus.SERVICE_UNAVAILABLE;
    }
}
