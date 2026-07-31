package com.nextai.document.service;

import com.nextai.common.ConflictException;
import com.nextai.common.ResourceNotFoundException;
import com.nextai.document.api.DocumentResponse;
import com.nextai.document.domain.DocumentEntity;
import com.nextai.document.domain.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");

    private final DocumentRepository documentRepository;
    private final DocumentIndexingService indexingService;
    private final DocumentObjectStorage objectStorage;
    private final String embeddingModel;

    public DocumentService(DocumentRepository documentRepository, DocumentIndexingService indexingService,
                           DocumentObjectStorage objectStorage,
                           @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel) {
        this.documentRepository = documentRepository;
        this.indexingService = indexingService;
        this.objectStorage = objectStorage;
        this.embeddingModel = embeddingModel;
    }

    public DocumentResponse upload(String ownerId, MultipartFile file) {
        validateFile(file);
        String contentHash = sha256(file);
        var existing = documentRepository.findByOwnerIdAndContentSha256(ownerId, contentHash);
        if (existing.isPresent()) {
            return DocumentResponse.from(existing.get());
        }

        String filename = safeFilename(file.getOriginalFilename());
        DocumentEntity document = documentRepository.saveAndFlush(new DocumentEntity(
                ownerId, filename, "pending", contentHash, embeddingModel, (short) 1536
        ));

        try {
            try (InputStream inputStream = file.getInputStream()) {
                String objectKey = objectStorage.store(ownerId, document.getId(), filename,
                        file.getContentType(), file.getSize(), inputStream);
                document.setStoragePath(objectKey);
            }
            documentRepository.save(document);
            indexingService.index(ownerId, document.getId());
            return DocumentResponse.from(document);
        } catch (IOException | ObjectStorageException exception) {
            document.markFailed();
            documentRepository.save(document);
            throw new ConflictException("Could not store uploaded file");
        }
    }

    public DocumentResponse getDocument(String ownerId, Long documentId) {
        return DocumentResponse.from(findDocument(ownerId, documentId));
    }

    public DocumentEntity findDocument(String ownerId, Long documentId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionSuffix(filename).replace(".", "");
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, DOC, DOCX, TXT, MD");
        }
    }

    private String safeFilename(String input) {
        String filename = StringUtils.getFilename(StringUtils.cleanPath(input == null ? "" : input));
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("A valid filename is required");
        }
        return filename;
    }

    private String extensionSuffix(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return sha256(inputStream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read uploaded file", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                value.append(String.format("%02x", part));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
