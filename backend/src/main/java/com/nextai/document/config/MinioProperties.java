package com.nextai.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.object-storage.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
