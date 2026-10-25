package com.orion.sources;

/** Consulta dirigida a un conector de datos. */
public record SourceQuery(Operation operation, String value) {

    public static SourceQuery of(Operation op, String value) {
        return new SourceQuery(op, value == null ? "" : value.trim());
    }

    public String valueLower() { return value.toLowerCase(); }
}
