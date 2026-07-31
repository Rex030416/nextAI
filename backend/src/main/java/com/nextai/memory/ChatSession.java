package com.nextai.memory;

import java.util.UUID;

public record ChatSession(UUID id, String ownerId, Long documentId, String summary, int summarizedMessageCount) {
}
