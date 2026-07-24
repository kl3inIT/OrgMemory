package com.orgmemory.graphrag.export;

public enum GraphExportFormat {
    JSON("application/json", "json"),
    CSV("text/csv", "csv"),
    MARKDOWN("text/markdown", "md"),
    TEXT("text/plain", "txt");

    private final String mediaType;
    private final String extension;

    GraphExportFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
