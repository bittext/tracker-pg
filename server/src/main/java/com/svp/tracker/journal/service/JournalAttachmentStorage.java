package com.svp.tracker.journal.service;

import com.svp.tracker.config.JournalProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JournalAttachmentStorage {

    private final JournalProperties journalProperties;

    public Path rootDir() {
        String configured = journalProperties.getStorageDirectory();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "tracker-journal").toAbsolutePath().normalize();
    }

    public Path pathForKey(String storageKey) {
        return rootDir().resolve(storageKey);
    }

    public String store(InputStream in) throws IOException {
        Files.createDirectories(rootDir());
        String key = UUID.randomUUID().toString();
        Path target = pathForKey(key);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    public void deleteFile(String storageKey) {
        try {
            Files.deleteIfExists(pathForKey(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
