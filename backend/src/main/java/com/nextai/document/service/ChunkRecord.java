package com.nextai.document.service;

import java.util.List;

record ChunkRecord(int chunkIndex, Integer pageNumber, String content, List<String> keywordTerms, float[] embedding) {
}
