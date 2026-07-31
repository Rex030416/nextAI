package com.nextai.document.service;

import com.nextai.document.domain.DocumentEntity;
import com.nextai.document.domain.DocumentRepository;
import org.apache.tika.Tika;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

@Service
public class DocumentIndexingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkJdbcRepository chunkRepository;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final DocumentObjectStorage objectStorage;
    private final SemanticAnswerCacheService semanticCacheService;
    private final Tika tika = new Tika();

    public DocumentIndexingService(DocumentRepository documentRepository, DocumentChunkJdbcRepository chunkRepository,
                                   TextChunker textChunker, EmbeddingModel embeddingModel,
                                   DocumentObjectStorage objectStorage, SemanticAnswerCacheService semanticCacheService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
        this.objectStorage = objectStorage;
        this.semanticCacheService = semanticCacheService;
    }

    @Async
    public void index(String ownerId, Long documentId) {
        DocumentEntity document = documentRepository.findByIdAndOwnerId(documentId, ownerId).orElseThrow();
        document.startIndexing();
        documentRepository.save(document);

        try {
            String extractedText;
            try (InputStream objectStream = objectStorage.open(document.getStoragePath())) {
                extractedText = tika.parseToString(objectStream);
            }
            List<ChunkRecord> chunks = textChunker.split(extractedText).stream()
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .map(text -> new ChunkRecord(0, null, text, KeywordTokenizer.tokenize(text), embeddingModel.embed(text)))
                    .toList();

            List<ChunkRecord> indexedChunks = java.util.stream.IntStream.range(0, chunks.size())
                    .mapToObj(index -> {
                        ChunkRecord chunk = chunks.get(index);
                        return new ChunkRecord(index, chunk.pageNumber(), chunk.content(), chunk.keywordTerms(), chunk.embedding());
                    })
                    .toList();

            if (indexedChunks.isEmpty()) {
                throw new IllegalArgumentException("No readable text was extracted from the uploaded document");
            }
            chunkRepository.replaceChunks(ownerId, documentId, indexedChunks);
            semanticCacheService.evictDocument(ownerId, documentId);
            document.markReady();
            documentRepository.save(document);
        } catch (Exception exception) {
            log.error("Document indexing failed: ownerId={}, documentId={}", ownerId, documentId, exception);
            document.markFailed();
            documentRepository.save(document);
        }
    }
}
