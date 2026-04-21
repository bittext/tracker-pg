package com.svp.tracker.management.service;

import com.svp.tracker.config.ManagementProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ManagementDayOneAttachmentStorage {

    private final ManagementProperties managementProperties;

    public Path baseDir() throws IOException {
        String configured = managementProperties.getDayOneStorageDirectory();
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured).toAbsolutePath().normalize();
            Files.createDirectories(p);
            return p;
        }
        Path p = Path.of(System.getProperty("java.io.tmpdir"), "tracker-day-one").toAbsolutePath().normalize();
        Files.createDirectories(p);
        return p;
}

    /** Returns relative storage key (posix-style) for DB. */
    public String store(MultipartFile file, long ownerUserId, long entryId) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        String key = ownerUserId + "/" + entryId + "_" + UUID.randomUUID() + "_" + safe;
        Path target = baseDir().resolve(key.replace('/', java.io.File.separatorChar));
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return key.replace('\\', '/');
    }

    public Path resolveFile(String storageKey) throws IOException {
        Path base = baseDir();
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("Invalid storage key");
        }
        return resolved;
    }
}
