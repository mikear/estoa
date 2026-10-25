package com.orion.transform;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.OrionEntity;

import java.util.ArrayList;
import java.util.List;

/** Resultado de ejecutar una transformacion sobre una entidad. */
public class TransformResult {

    private final boolean ok;
    private final String transformId;
    private final String message;
    private final long durationMs;
    private final List<OrionEntity> entities = new ArrayList<>();
    private final List<EdgeDraft> edges = new ArrayList<>();

    public TransformResult(boolean ok, String transformId, String message, long durationMs) {
        this.ok = ok;
        this.transformId = transformId;
        this.message = message;
        this.durationMs = durationMs;
    }

    public TransformResult entity(OrionEntity e) {
        if (e != null) entities.add(e);
        return this;
    }

    public TransformResult edge(EdgeDraft e) {
        if (e != null) edges.add(e);
        return this;
    }

    public boolean isOk() { return ok; }
    public String getTransformId() { return transformId; }
    public String getMessage() { return message; }
    public long getDurationMs() { return durationMs; }
    public List<OrionEntity> getEntities() { return entities; }
    public List<EdgeDraft> getEdges() { return edges; }
}
