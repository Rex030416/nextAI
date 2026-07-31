package com.nextai.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    Optional<DocumentEntity> findByIdAndOwnerId(Long id, String ownerId);
    Optional<DocumentEntity> findByOwnerIdAndContentSha256(String ownerId, String contentSha256);
}
