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
                        throw new IOException(s3FailureMessage("put", key, retry), retry);
                    }
                }
                throw new IOException(s3FailureMessage("put", key, e), e);
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
                    throw new IOException(s3FailureMessage("delete", storageKey, retry), retry);
                }
            }
            throw new IOException(s3FailureMessage("delete", storageKey, e), e);
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
                    throw new IOException(s3FailureMessage("get", storageKey, retry), retry);
                }
            }
            throw new IOException(s3FailureMessage("get", storageKey, e), e);
        }
    }

    private String s3FailureMessage(String op, String objectKey, SdkException e) {
        return "S3 " + op + " failed: bucket=" + bucket + " key=" + objectKey + " — " + sdkSummary(e);
    }

    private static String sdkSummary(SdkException e) {
        if (e instanceof S3Exception s3 && s3.awsErrorDetails() != null) {
            var d = s3.awsErrorDetails();
            String code = d.errorCode();
            String msg = d.errorMessage();
            StringBuilder sb = new StringBuilder();
            if (code != null && !code.isBlank()) {
                sb.append(code);
            } else {
                sb.append("S3Exception");
            }
            if (msg != null && !msg.isBlank()) {
                sb.append(": ").append(msg.trim());
            } else if (e.getMessage() != null) {
                sb.append(": ").append(e.getMessage().trim());
            }
            int status = s3.statusCode();
            if (status > 0) {
                sb.append(" (HTTP ").append(status).append(')');
            }
            return sb.toString();
        }
        return e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage().trim() : e.getClass().getSimpleName();
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
