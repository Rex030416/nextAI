package com.nextai.document.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps paragraph boundaries intact. Only a paragraph exceeding the limit is split further,
 * preferring sentence-ending punctuation before lower-priority separators.
 */
@Component
public class TextChunker {

    private static final int CHUNK_SIZE = 500;
    private static final String[] SEPARATORS = {"。", "！", "？", "；", ". ", "! ", "? ", "; ", "，", ", ", " ", ""};

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n+")) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            splitLongParagraph(normalized, chunks);
        }
        return chunks;
    }

    private void splitLongParagraph(String text, List<String> chunks) {
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return;
        }

        int splitAt = findBoundary(text);
        if (splitAt <= 0 || splitAt >= text.length()) {
            splitAt = CHUNK_SIZE;
        }
        chunks.add(text.substring(0, splitAt).trim());
        splitLongParagraph(text.substring(splitAt).trim(), chunks);
    }

    private int findBoundary(String text) {
        int upperBound = Math.min(CHUNK_SIZE, text.length());
        for (String separator : SEPARATORS) {
            if (separator.isEmpty()) {
                return upperBound;
            }
            int boundary = text.lastIndexOf(separator, upperBound - 1);
            if (boundary >= 0) {
                return boundary + separator.length();
            }
        }
        return upperBound;
    }
}
