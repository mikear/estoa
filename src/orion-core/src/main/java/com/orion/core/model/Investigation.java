package com.orion.core.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de investigacion: unidad de trabajo del analista. Agrupa el grafo de
 * entidades, etiquetas, notas y metadatos de trazabilidad.
 */
public class Investigation {

    private static final DateTimeFormatter NOTE_TS =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final String id;
    private String name;
    private String description = "";
    private InvestigationStatus status = InvestigationStatus.OPEN;
    private Priority priority = Priority.MEDIUM;
    private String analyst = "analista";
    private String classification = "TLP:AMBER";
    private final List<String> tags = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Investigation(String name) {
        this("CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), name);
    }

    /** Reconstruye un caso existente conservando su identificador. */
    public Investigation(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public void addTag(String tag) {
        String t = tag.trim();
        if (!t.isEmpty() && !tags.contains(t)) tags.add(t);
        touch();
    }

    public void addNote(String note) {
        if (note != null && !note.isBlank()) notes.add(NOTE_TS.format(Instant.now()) + " | " + note.trim());
        touch();
    }

    /** Reformatea notas antiguas guardadas con marca ISO a fecha local legible. */
    public static String displayNote(String raw) {
        int sep = raw.indexOf(" | ");
        if (sep > 0) {
            try {
                return NOTE_TS.format(Instant.parse(raw.substring(0, sep))) + raw.substring(sep);
            } catch (RuntimeException ignored) {
                // nota sin marca de tiempo valida: se muestra tal cual
            }
        }
        return raw;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("status", status.name());
        m.put("priority", priority.name());
        m.put("analyst", analyst);
        m.put("classification", classification);
        m.put("tags", List.copyOf(tags));
        m.put("notes", List.copyOf(notes));
        m.put("createdAt", createdAt.toString());
        m.put("updatedAt", updatedAt.toString());
        return m;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }
    public InvestigationStatus getStatus() { return status; }
    public void setStatus(InvestigationStatus status) { this.status = status; touch(); }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; touch(); }
    public String getAnalyst() { return analyst; }
    public void setAnalyst(String analyst) { this.analyst = analyst; touch(); }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; touch(); }
    public List<String> getTags() { return tags; }
    public List<String> getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Restaura las fechas originales al cargar un caso persistido. */
    public void restoreTimestamps(Instant createdAt, Instant updatedAt) {
        if (createdAt != null) this.createdAt = createdAt;
        if (updatedAt != null) this.updatedAt = updatedAt;
    }
}
