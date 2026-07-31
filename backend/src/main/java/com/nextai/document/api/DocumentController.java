package com.nextai.document.api;

import com.nextai.document.service.DocumentService;
import com.nextai.document.service.QuestionAnswerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final QuestionAnswerService questionAnswerService;
    private final OwnerIdResolver ownerIdResolver;

    public DocumentController(DocumentService documentService, QuestionAnswerService questionAnswerService,
                              OwnerIdResolver ownerIdResolver) {
        this.documentService = documentService;
        this.questionAnswerService = questionAnswerService;
        this.ownerIdResolver = ownerIdResolver;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @RequestHeader(value = "X-Owner-Id", required = false) String ownerIdHeader,
            @RequestPart("file") MultipartFile file) {
        DocumentResponse response = documentService.upload(ownerIdResolver.resolve(ownerIdHeader), file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.documentId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse getDocument(
            @RequestHeader(value = "X-Owner-Id", required = false) String ownerIdHeader,
            @PathVariable Long documentId) {
        return documentService.getDocument(ownerIdResolver.resolve(ownerIdHeader), documentId);
    }

    @PostMapping("/{documentId}/questions")
    public AskDocumentResponse ask(
            @RequestHeader(value = "X-Owner-Id", required = false) String ownerIdHeader,
            @PathVariable Long documentId,
            @Valid @RequestBody AskDocumentRequest request) {
        return questionAnswerService.ask(ownerIdResolver.resolve(ownerIdHeader), documentId,
                request.question(), request.sessionId());
    }
}
