package com.nextai.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KeywordTokenizer {
    private static final Pattern CHINESE_SEQUENCE = Pattern.compile("[\\u3400-\\u9fff]+");
    private static final Pattern LATIN_WORD = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");

    private KeywordTokenizer() {
    }

    static List<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        Matcher chineseMatcher = CHINESE_SEQUENCE.matcher(normalized);
        while (chineseMatcher.find()) {
            String sequence = chineseMatcher.group();
            for (int index = 0; index < sequence.length(); index++) {
                tokens.add(String.valueOf(sequence.charAt(index)));
                if (index + 1 < sequence.length()) {
                    tokens.add(sequence.substring(index, index + 2));
                }
            }
        }
        Matcher wordMatcher = LATIN_WORD.matcher(normalized);
        while (wordMatcher.find()) {
            tokens.add(wordMatcher.group());
        }
        return tokens.stream().distinct().toList();
    }
}
