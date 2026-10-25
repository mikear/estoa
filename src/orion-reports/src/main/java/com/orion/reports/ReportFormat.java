package com.orion.reports;

/** Formatos de exportacion soportados. */
public enum ReportFormat {
    PDF("application/pdf", "pdf"),
    HTML("text/html", "html"),
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
    PNG("image/png", "png"),
    SVG("image/svg+xml", "svg");

    private final String mime;
    private final String extension;

    ReportFormat(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() { return mime; }
    public String extension() { return extension; }
}
