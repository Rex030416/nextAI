package com.nextai.document.service;

public record SemanticCacheEntry(Long id, String answer, String sourcesJson, double similarity) {
}
