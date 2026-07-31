package com.nextai.document.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

@Repository
public class DocumentChunkJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceChunks(String ownerId, Long documentId, List<ChunkRecord> chunks) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE owner_id = ? AND document_id = ?", ownerId, documentId);

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO document_chunks (owner_id, document_id, chunk_index, content, keyword_terms, embedding, metadata)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?::jsonb)
                """,
                chunks,
                100,
                (statement, chunk) -> {
                    statement.setString(1, ownerId);
                    statement.setLong(2, documentId);
                    statement.setInt(3, chunk.chunkIndex());
                    statement.setString(4, chunk.content());
                    try {
                        statement.setArray(5, statement.getConnection().createArrayOf("text", chunk.keywordTerms().toArray()));
                    } catch (SQLException exception) {
                        throw new IllegalStateException("Unable to create keyword array", exception);
                    }
                    statement.setString(6, vectorLiteral(chunk.embedding()));
                    statement.setString(7, "{\"chunkIndex\":" + chunk.chunkIndex() + "}");
                }
        );
    }

    public List<RetrievedChunk> findNearest(String ownerId, Long documentId, float[] queryEmbedding, int limit) {
        return jdbcTemplate.query(
                """
                SELECT chunk_index, page_number, content
                FROM document_chunks
                WHERE owner_id = ? AND document_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getInt("chunk_index"),
                        resultSet.getObject("page_number", Integer.class),
                        resultSet.getString("content")
                ),
                ownerId, documentId, vectorLiteral(queryEmbedding), limit
        );
    }

    public List<RetrievedChunk> findKeywordMatches(String ownerId, Long documentId, List<String> terms, int limit) {
        if (terms.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.execute((PreparedStatementCreator) connection -> {
            var statement = connection.prepareStatement("""
                    SELECT chunk_index, page_number, content
                    FROM document_chunks
                    WHERE owner_id = ? AND document_id = ? AND keyword_terms && ?
                    ORDER BY cardinality(ARRAY(
                        SELECT unnest(keyword_terms) INTERSECT SELECT unnest(?::text[])
                    )) DESC
                    LIMIT ?
                    """);
            statement.setString(1, ownerId);
            statement.setLong(2, documentId);
            var termArray = connection.createArrayOf("text", terms.toArray());
            statement.setArray(3, termArray);
            statement.setArray(4, termArray);
            statement.setInt(5, limit);
            return statement;
        }, (PreparedStatementCallback<List<RetrievedChunk>>) statement -> {
            try (var resultSet = statement.executeQuery()) {
                var results = new java.util.ArrayList<RetrievedChunk>();
                while (resultSet.next()) {
                    results.add(new RetrievedChunk(
                            resultSet.getInt("chunk_index"),
                            resultSet.getObject("page_number", Integer.class),
                            resultSet.getString("content")
                    ));
                }
                return results;
            }
        });
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
