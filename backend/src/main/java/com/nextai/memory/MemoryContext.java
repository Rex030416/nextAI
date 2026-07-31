package com.nextai.memory;

import java.util.List;
import java.util.UUID;

public record MemoryContext(UUID sessionId, String summary, List<MemoryMessage> recentMessages,
                            List<String> longTermMemories) {
    public boolean isContextFree() {
        return summary.isBlank() && recentMessages.isEmpty() && longTermMemories.isEmpty();
    }
}
