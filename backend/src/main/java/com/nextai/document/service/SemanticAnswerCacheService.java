package com.nextai.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextai.document.api.AskDocumentResponse;
import com.nextai.document.api.SourceReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class SemanticAnswerCacheService {
    private final SemanticAnswerCacheRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final double minimumSimilarity;
    private final long ttlHours;

    public SemanticAnswerCacheService(SemanticAnswerCacheRepository repository, ObjectMapper objectMapper,
                                      @Value("${app.semantic-cache.enabled:true}") boolean enabled,
                                      @Value("${app.semantic-cache.minimum-similarity:0.92}") double minimumSimilarity,
                                      @Value("${app.semantic-cache.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.minimumSimilarity = minimumSimilarity;
        this.ttlHours = ttlHours;
    }

    public Optional<SemanticCacheHit> find(String ownerId, Long documentId, float[] questionEmbedding) {
        if (!enabled) {
            return Optional.empty();
        }
        return repository.findNearest(ownerId, documentId, questionEmbedding)
                .filter(entry -> entry.similarity() >= minimumSimilarity)
                .map(entry -> {
                    try {
                        List<SourceReference> sources = objectMapper.readValue(entry.sourcesJson(),
                                new TypeReference<List<SourceReference>>() { });
                        repository.recordHit(entry.id());
                        return new SemanticCacheHit(entry.answer(), sources, entry.similarity());
                    } catch (JsonProcessingException exception) {
                        return null;
                    }
                });
    }

    public void put(String ownerId, Long documentId, String question, float[] questionEmbedding,
                    String answer, List<SourceReference> sources) {
        if (!enabled) {
            return;
        }
        try {
            repository.insert(ownerId, documentId, question, questionEmbedding, answer,
                    objectMapper.writeValueAsString(sources), Instant.now().plus(ttlHours, ChronoUnit.HOURS));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize semantic cache sources", exception);
        }
    }

    public void evictDocument(String ownerId, Long documentId) {
        repository.evictDocument(ownerId, documentId);
    }

    public record SemanticCacheHit(String answer, List<SourceReference> sources, double similarity) {
    }
}
