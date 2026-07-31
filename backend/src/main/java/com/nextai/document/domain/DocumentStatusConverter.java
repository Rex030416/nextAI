package com.nextai.document.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DocumentStatusConverter implements AttributeConverter<DocumentStatus, String> {

    @Override
    public String convertToDatabaseColumn(DocumentStatus attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public DocumentStatus convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : DocumentStatus.fromDatabaseValue(databaseValue);
    }
}
