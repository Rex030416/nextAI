package com.nextai.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationMemoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConversationMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ChatSession> findSession(UUID sessionId, String ownerId) {
        return jdbcTemplate.query("""
                        SELECT id, owner_id, document_id, summary, summarized_message_count
                        FROM chat_sessions WHERE id = ? AND owner_id = ?
                        """,
                (resultSet, rowNumber) -> new ChatSession(
                        resultSet.getObject("id", UUID.class), resultSet.getString("owner_id"),
                        resultSet.getLong("document_id"), resultSet.getString("summary"),
                        resultSet.getInt("summarized_message_count")), sessionId, ownerId).stream().findFirst();
    }

    public void createSession(UUID sessionId, String ownerId, Long documentId) {
        jdbcTemplate.update("INSERT INTO chat_sessions (id, owner_id, document_id) VALUES (?, ?, ?)",
                sessionId, ownerId, documentId);
    }

    public void appendMessage(UUID sessionId, String ownerId, String role, String content) {
        jdbcTemplate.update("""
                        INSERT INTO chat_messages (session_id, owner_id, role, content)
                        VALUES (?, ?, ?, ?)
                        """, sessionId, ownerId, role, content);
        jdbcTemplate.update("UPDATE chat_sessions SET updated_at = NOW() WHERE id = ? AND owner_id = ?", sessionId, ownerId);
    }

    public List<MemoryMessage> findRecentMessages(UUID sessionId, int limit) {
        List<MemoryMessage> messages = jdbcTemplate.query("""
                        SELECT role, content FROM chat_messages
                        WHERE session_id = ? ORDER BY id DESC LIMIT ?
                        """, (resultSet, rowNumber) -> new MemoryMessage(
                        resultSet.getString("role"), resultSet.getString("content")), sessionId, limit);
        Collections.reverse(messages);
        return messages;
    }

    public int countMessages(UUID sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_messages WHERE session_id = ?", Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    public List<MemoryMessage> findMessages(UUID sessionId, int offset, int limit) {
        return jdbcTemplate.query("""
                        SELECT role, content FROM chat_messages
                        WHERE session_id = ? ORDER BY id ASC OFFSET ? LIMIT ?
                        """, (resultSet, rowNumber) -> new MemoryMessage(
                        resultSet.getString("role"), resultSet.getString("content")), sessionId, offset, limit);
    }

    public void updateSummary(UUID sessionId, String ownerId, String summary, int summarizedMessageCount) {
        jdbcTemplate.update("""
                        UPDATE chat_sessions
                        SET summary = ?, summarized_message_count = ?, updated_at = NOW()
                        WHERE id = ? AND owner_id = ?
                        """, summary, summarizedMessageCount, sessionId, ownerId);
    }

    public List<String> findRelevantUserMemories(String ownerId, float[] queryEmbedding, int limit) {
        return jdbcTemplate.query("""
                        SELECT content FROM user_memories
                        WHERE owner_id = ?
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """, (resultSet, rowNumber) -> resultSet.getString("content"),
                ownerId, vectorLiteral(queryEmbedding), limit);
    }

    public void addUserMemory(String ownerId, UUID sourceSessionId, String content, float[] embedding) {
        jdbcTemplate.update("""
                        INSERT INTO user_memories (owner_id, source_session_id, content, embedding)
                        VALUES (?, ?, ?, ?::vector)
                        """, ownerId, sourceSessionId, content, vectorLiteral(embedding));
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
