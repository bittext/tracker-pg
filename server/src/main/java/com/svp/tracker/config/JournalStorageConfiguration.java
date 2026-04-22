package com.svp.tracker.config;

import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.journal.service.LocalFileJournalBlobStore;
import com.svp.tracker.journal.service.S3JournalBlobStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class JournalStorageConfiguration {

    @Bean
    public JournalBlobStore journalBlobStore(JournalProperties properties) {
        String bucket = properties.getS3Bucket();
        if (bucket == null || bucket.isBlank()) {
            return new LocalFileJournalBlobStore(properties);
        }
        String region = properties.getS3Region();
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        } else {
            region = region.trim();
        }
        S3Client client = S3Client.builder()
                .region(Region.of(region))
                .build();
        return new S3JournalBlobStore(bucket.trim(), client);
    }
}
