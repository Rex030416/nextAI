package com.nextai.memory;

import com.nextai.common.ConflictException;
import com.nextai.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationMemoryService {
    private static final int RECENT_MESSAGE_LIMIT = 6;
    private static final int LONG_TERM_MEMORY_LIMIT = 3;

    private final ConversationMemoryRepository repository;

    public ConversationMemoryService(ConversationMemoryRepository repository) {
        this.repository = repository;
    }

    public MemoryContext openContext(String ownerId, Long documentId, UUID requestedSessionId, float[] queryEmbedding) {
        ChatSession session;
        if (requestedSessionId == null) {
            UUID newSessionId = UUID.randomUUID();
            repository.createSession(newSessionId, ownerId, documentId);
            session = repository.findSession(newSessionId, ownerId).orElseThrow();
        } else {
            session = repository.findSession(requestedSessionId, ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
            if (!session.documentId().equals(documentId)) {
                throw new ConflictException("The chat session belongs to a different document");
            }
        }
        return new MemoryContext(session.id(), session.summary(),
                repository.findRecentMessages(session.id(), RECENT_MESSAGE_LIMIT),
                repository.findRelevantUserMemories(ownerId, queryEmbedding, LONG_TERM_MEMORY_LIMIT));
    }

    public void appendTurn(String ownerId, UUID sessionId, String question, String answer) {
        repository.appendMessage(sessionId, ownerId, "user", question);
        repository.appendMessage(sessionId, ownerId, "assistant", answer);
    }
}
