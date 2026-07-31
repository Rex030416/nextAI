package com.nextai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private String ownerId;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel;

    @Column(name = "embedding_dimensions", nullable = false)
    private short embeddingDimensions;

    @Convert(converter = DocumentStatusConverter.class)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(String ownerId, String originalFilename, String storagePath, String contentSha256,
                          String embeddingModel, short embeddingDimensions) {
        this.ownerId = ownerId;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.contentSha256 = contentSha256;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public void startIndexing() {
        this.status = DocumentStatus.INDEXING;
    }

    public void markReady() {
        this.status = DocumentStatus.READY;
        this.indexedAt = Instant.now();
    }

    public void markFailed() {
        this.status = DocumentStatus.FAILED;
    }

    public Long getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoragePath() { return storagePath; }
    public String getContentSha256() { return contentSha256; }
    public String getEmbeddingModel() { return embeddingModel; }
    public short getEmbeddingDimensions() { return embeddingDimensions; }
    public DocumentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getIndexedAt() { return indexedAt; }
}
