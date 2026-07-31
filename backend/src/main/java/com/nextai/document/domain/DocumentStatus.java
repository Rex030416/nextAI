package com.nextai.document.domain;

public enum DocumentStatus {
    UPLOADED("uploaded"),
    INDEXING("indexing"),
    READY("ready"),
    FAILED("failed");

    private final String databaseValue;

    DocumentStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static DocumentStatus fromDatabaseValue(String value) {
        for (DocumentStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown document status: " + value);
    }
}
