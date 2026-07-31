package com.nextai.document.api;

import java.util.UUID;
import java.util.List;

public record AskDocumentResponse(String answer, List<SourceReference> sources, UUID sessionId,
                                  boolean cacheHit, Double cacheSimilarity) {
}
