package com.svp.tracker.common.web;

import com.svp.tracker.finance.service.RobinhoodAgenticUnauthorizedException;
import com.svp.tracker.fitness.exception.NotFoundException;
import java.io.UncheckedIOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> fitnessNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> dataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "data_access", "message", "A database error occurred."));
    }

    @ExceptionHandler(RobinhoodAgenticUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> robinhoodUnauthorized(RobinhoodAgenticUnauthorizedException ex) {
        log.warn("Robinhood sidecar unauthorized: {}", ex.getMessage());
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Robinhood credentials were rejected.";
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthorized", "message", message));
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<Map<String, String>> unexpectedRollback(UnexpectedRollbackException ex) {
        log.error("Transaction rolled back after a nested failure was handled", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error",
                        "transaction_rollback",
                        "message",
                        "The request could not be completed because a nested operation failed. Check server logs."));
    }

    /**
     * JDBC / JPA failures often surface as {@link TransactionException} (e.g. cannot open connection) which is not a
     * {@link DataAccessException}, and would otherwise produce an opaque 500 from Spring Boot’s default handler.
     */
    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, String>> transaction(TransactionException ex) {
        log.error("Transaction / database availability error", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error",
                        "database_unavailable",
                        "message",
                        "The database is unavailable or could not complete the request. Try again later."));
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> storageIo(UncheckedIOException ex) {
        log.error("Storage I/O error", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error",
                        "storage_io",
                        "message",
                        "Attachment storage failed. Check server configuration and try again."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
        log.error("Illegal state", ex);
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "The request could not be completed.";
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "illegal_state", "message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> multipartTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Multipart upload rejected: {}", ex.getMessage());
        return uploadTooLargeResponse();
    }

    /** Tomcat may surface size limits as a plain MultipartException wrapping FileSizeLimitExceededException. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> multipart(MultipartException ex) {
        Throwable cause = ex;
        while (cause != null) {
            String name = cause.getClass().getSimpleName();
            if (name.contains("FileSizeLimit") || name.contains("SizeLimit") || name.contains("MaxUpload")) {
                log.warn("Multipart upload rejected: {}", ex.getMessage());
                return uploadTooLargeResponse();
            }
            cause = cause.getCause();
        }
        log.warn("Multipart request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error",
                        "bad_request",
                        "message",
                        "Invalid multipart upload. Try fewer or smaller files."));
    }

    private static ResponseEntity<Map<String, String>> uploadTooLargeResponse() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "error",
                        "payload_too_large",
                        "message",
                        "A file exceeds the 100MB per-file upload limit. Split or compress the recording, then retry. (Whisper transcription still needs files under 25MB.)"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }
        return ResponseEntity.status(status)
                .body(Map.of("error", status.name().toLowerCase(), "message", message));
    }
}
