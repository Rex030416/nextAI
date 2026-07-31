package com.nextai.document.api;

import com.nextai.document.domain.DocumentEntity;
import com.nextai.document.domain.DocumentStatus;

import java.time.Instant;

public record DocumentResponse(
        Long documentId,
        String filename,
        DocumentStatus status,
        Instant createdAt,
        Instant indexedAt
) {
    public static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getIndexedAt()
        );
    }
}
