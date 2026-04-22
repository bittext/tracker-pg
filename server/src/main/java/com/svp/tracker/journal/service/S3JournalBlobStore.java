package com.svp.tracker.journal.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Stores attachment bytes in a single S3 bucket; object keys are namespaced by user and entry. */
public class S3JournalBlobStore implements JournalBlobStore, DisposableBean {

    private final String bucket;
    private final S3Client client;

    public S3JournalBlobStore(String bucket, S3Client client) {
        this.bucket = bucket;
        this.client = client;
    }

    @Override
    public String put(long ownerUserId, long entryId, InputStream in, long sizeBytes) throws IOException {
        String key = "journal/" + ownerUserId + "/" + entryId + "/" + UUID.randomUUID();
        try (InputStream input = in) {
            byte[] payload = input.readAllBytes();
            try {
                client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromBytes(payload));
            } catch (SdkException e) {
                String redirectRegion = redirectedRegion(e);
                if (redirectRegion != null) {
                    try (S3Client regional = clientForRegion(redirectRegion)) {
                        regional.putObject(
                                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                                RequestBody.fromBytes(payload));
                        return key;
                    } catch (SdkException retry) {
                        throw new IOException("S3 put failed: " + key, retry);
                    }
                }
                throw new IOException("S3 put failed: " + key, e);
            }
        }
        return key;
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (SdkException e) {
            String redirectRegion = redirectedRegion(e);
            if (redirectRegion != null) {
                try (S3Client regional = clientForRegion(redirectRegion)) {
                    regional.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
                    return;
                } catch (SdkException retry) {
                    throw new IOException("S3 delete failed: " + storageKey, retry);
                }
            }
            throw new IOException("S3 delete failed: " + storageKey, e);
        }
    }

    @Override
    public byte[] readAllBytes(String storageKey) throws IOException {
        try {
            return client
                    .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
                    .asByteArray();
        } catch (SdkException e) {
            String redirectRegion = redirectedRegion(e);
            if (redirectRegion != null) {
                try (S3Client regional = clientForRegion(redirectRegion)) {
                    return regional
                            .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
                            .asByteArray();
                } catch (SdkException retry) {
                    throw new IOException("S3 get failed: " + storageKey, retry);
                }
            }
            throw new IOException("S3 get failed: " + storageKey, e);
        }
    }

    private static String redirectedRegion(SdkException e) {
        if (e instanceof S3Exception s3 && s3.awsErrorDetails() != null) {
            return s3.awsErrorDetails().sdkHttpResponse().firstMatchingHeader("x-amz-bucket-region").orElse(null);
        }
        return null;
    }

    private static S3Client clientForRegion(String region) {
        return S3Client.builder().region(Region.of(region)).crossRegionAccessEnabled(true).build();
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }
}
