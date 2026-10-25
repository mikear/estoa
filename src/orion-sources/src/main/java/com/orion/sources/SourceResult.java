package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.OrionEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado estandarizado de un conector: entidades descubiertas, aristas
 * implicitas y metadatos. Los conectores nunca lanzan excepciones; devuelven
 * un resultado con ok=false y el mensaje de error.
 */
public class SourceResult {

    private final boolean ok;
    private final String sourceId;
    private final String message;
    private final List<OrionEntity> entities = new ArrayList<>();
    private final List<EdgeDraft> edges = new ArrayList<>();
    private final Map<String, Object> raw = new LinkedHashMap<>();

    private SourceResult(boolean ok, String sourceId, String message) {
        this.ok = ok;
        this.sourceId = sourceId;
        this.message = message;
    }

    public static SourceResult ok(String sourceId, String message) {
        return new SourceResult(true, sourceId, message);
    }

    public static SourceResult error(String sourceId, String message) {
        return new SourceResult(false, sourceId, message);
    }

    public SourceResult entity(OrionEntity e) {
        if (e != null) entities.add(e);
        return this;
    }

    public SourceResult edge(EdgeDraft e) {
        if (e != null) edges.add(e);
        return this;
    }

    public SourceResult raw(String key, Object value) {
        raw.put(key, value);
        return this;
    }

    public boolean isOk() { return ok; }
    public String getSourceId() { return sourceId; }
    public String getMessage() { return message; }
    public List<OrionEntity> getEntities() { return entities; }
    public List<EdgeDraft> getEdges() { return edges; }
    public Map<String, Object> getRaw() { return raw; }
}
