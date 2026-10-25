package com.orion.core.model;

/**
 * Borrador de arista emitido por conectores y transformaciones. Las referencias
 * son por valor canonico de entidad; el motor resuelve despues los IDs reales
 * contra el grafo, creando los nodos que falten.
 */
public record EdgeDraft(String fromValue, String toValue, RelationshipType relation, String label) {

    public static EdgeDraft of(String from, String to, RelationshipType rel) {
        return new EdgeDraft(from, to, rel, rel.label());
    }
}
