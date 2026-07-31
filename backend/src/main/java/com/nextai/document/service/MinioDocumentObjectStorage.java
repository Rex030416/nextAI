package com.nextai.document.service;

import com.nextai.document.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

@Service
public class MinioDocumentObjectStorage implements DocumentObjectStorage {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioDocumentObjectStorage(MinioProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception exception) {
            throw new ObjectStorageException("Could not connect to MinIO or create the document bucket", exception);
        }
    }

    @Override
    public String store(String ownerId, Long documentId, String filename, String contentType, long size,
                        InputStream inputStream) {
        String objectKey = objectKey(ownerId, documentId, filename);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception exception) {
            throw new ObjectStorageException("Could not upload the document to MinIO", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Could not download the document from MinIO", exception);
        }
    }

    private String objectKey(String ownerId, Long documentId, String filename) {
        return "owners/%s/documents/%d/%s%s".formatted(
                sha256(ownerId).substring(0, 24), documentId, UUID.randomUUID(), extensionSuffix(filename));
    }

    private String extensionSuffix(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
