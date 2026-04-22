package com.svp.tracker.journal.service;

import java.io.IOException;
import java.io.InputStream;

/** Persists binary journal attachment payloads (local files or S3). */
public interface JournalBlobStore {

    /**
     * Stores bytes and returns the key persisted in {@code journal_attachments.storage_key}.
     *
     * @param ownerUserId used for S3 key layout; may be ignored by local file storage
     * @param entryId used for S3 key layout; may be ignored by local file storage
     */
    String put(long ownerUserId, long entryId, InputStream in, long sizeBytes) throws IOException;

    void delete(String storageKey) throws IOException;

    byte[] readAllBytes(String storageKey) throws IOException;
}
