package com.nextai.document.service;

import java.io.InputStream;

public interface DocumentObjectStorage {
    String store(String ownerId, Long documentId, String filename, String contentType, long size, InputStream inputStream);

    InputStream open(String objectKey);
}
