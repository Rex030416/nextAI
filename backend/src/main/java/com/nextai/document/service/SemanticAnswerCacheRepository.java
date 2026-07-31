package com.nextai.document.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class SemanticAnswerCacheRepository {
    private final JdbcTemplate jdbcTemplate;

    public SemanticAnswerCacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SemanticCacheEntry> findNearest(String ownerId, Long documentId, float[] questionEmbedding) {
        return jdbcTemplate.query("""
                        SELECT id, answer, sources, 1 - (question_embedding <=> ?::vector) AS similarity
                        FROM semantic_answer_cache
                        WHERE owner_id = ? AND document_id = ? AND expires_at > NOW()
                        ORDER BY question_embedding <=> ?::vector
                        LIMIT 1
                        """, (resultSet, rowNumber) -> new SemanticCacheEntry(
                        resultSet.getLong("id"), resultSet.getString("answer"),
                        resultSet.getString("sources"), resultSet.getDouble("similarity")),
                vectorLiteral(questionEmbedding), ownerId, documentId, vectorLiteral(questionEmbedding)).stream().findFirst();
    }

    public void insert(String ownerId, Long documentId, String question, float[] questionEmbedding,
                       String answer, String sourcesJson, Instant expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO semantic_answer_cache
                          (owner_id, document_id, question, question_embedding, answer, sources, expires_at)
                        VALUES (?, ?, ?, ?::vector, ?, ?::jsonb, ?)
                        """, ownerId, documentId, question, vectorLiteral(questionEmbedding), answer, sourcesJson,
                Timestamp.from(expiresAt));
    }

    public void recordHit(Long cacheId) {
        jdbcTemplate.update("""
                        UPDATE semantic_answer_cache
                        SET hit_count = hit_count + 1, last_hit_at = NOW()
                        WHERE id = ?
                        """, cacheId);
    }

    public void evictDocument(String ownerId, Long documentId) {
        jdbcTemplate.update("DELETE FROM semantic_answer_cache WHERE owner_id = ? AND document_id = ?", ownerId, documentId);
    }

    private String vectorLiteral(float[] values) {
        StringBuilder vector = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                vector.append(',');
            }
            vector.append(values[index]);
        }
        return vector.append(']').toString();
    }
}
