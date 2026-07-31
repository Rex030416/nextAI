package com.nextai.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AskDocumentRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 4_000, message = "question must be at most 4000 characters")
        String question,
        UUID sessionId
) {
}
