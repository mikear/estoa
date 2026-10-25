package com.orion.persistence;

import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.InvestigationStatus;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.Priority;
import com.orion.core.model.RelationshipType;
import com.orion.core.model.Tlp;
import com.orion.core.util.JsonUtil;
import com.orion.graph.GraphEdge;
import com.orion.graph.OrionGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de persistencia del caso de investigacion: el grafo completo
 * (entidades, aristas, notas y metadatos) se serializa como documento JSON
 * junto a las columnas de listado para consultas rapidas.
 */
public class PersistenceService {

    /** Registro cargado desde la base de datos. */
    public record LoadedCase(Investigation investigation, OrionGraph graph) {}

    private final DataSource ds;
    private final boolean isPostgres;

    public PersistenceService(DataSource ds) {
        this.ds = ds;
        try (Connection c = ds.getConnection()) {
            this.isPostgres = c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
            Schema.init(c);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inicializar el esquema: " + e.getMessage(), e);
        }
    }

    public void save(Investigation inv, OrionGraph graph) {
        String json = serializeCase(inv, graph);
        String sql = isPostgres ? """
                INSERT INTO investigations (id, name, description, status, priority, analyst, classification,
                                            tags, created_at, updated_at, graph_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description,
                  status=EXCLUDED.status, priority=EXCLUDED.priority, analyst=EXCLUDED.analyst,
                  classification=EXCLUDED.classification, tags=EXCLUDED.tags,
                  created_at=EXCLUDED.created_at, updated_at=EXCLUDED.updated_at, graph_json=EXCLUDED.graph_json
                """ : """
                MERGE INTO investigations (id, name, description, status, priority, analyst, classification,
                                           tags, created_at, updated_at, graph_json)
                KEY (id) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, inv.getId());
            ps.setString(2, inv.getName());
            ps.setString(3, inv.getDescription());
            ps.setString(4, inv.getStatus().name());
            ps.setString(5, inv.getPriority().name());
            ps.setString(6, inv.getAnalyst());
            ps.setString(7, inv.getClassification());
            ps.setString(8, String.join(",", inv.getTags()));
            ps.setTimestamp(9, Timestamp(inv.getCreatedAt()));
            ps.setTimestamp(10, Timestamp(inv.getUpdatedAt()));
            ps.setString(11, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo guardar el caso: " + e.getMessage(), e);
        }
    }

    private static java.sql.Timestamp Timestamp(Instant i) {
        return java.sql.Timestamp.from(i);
    }

    public List<Investigation> listSummaries() {
        String sql = "SELECT id, name, description, status, priority, analyst, classification, tags, created_at, updated_at FROM investigations ORDER BY updated_at DESC";
        List<Investigation> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Investigation inv = new Investigation(rs.getString("id"), rs.getString("name"));
                setCommon(inv, rs);
                out.add(inv);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar casos: " + e.getMessage(), e);
        }
        return out;
    }

    private void setCommon(Investigation inv, ResultSet rs) throws SQLException {
        inv.setDescription(rs.getString("description") == null ? "" : rs.getString("description"));
        inv.setStatus(Enum.valueOf(InvestigationStatus.class, rs.getString("status")));
        inv.setPriority(Enum.valueOf(Priority.class, rs.getString("priority")));
        inv.setAnalyst(rs.getString("analyst"));
        inv.setClassification(rs.getString("classification"));
        String tags = rs.getString("tags");
        if (tags != null && !tags.isBlank()) {
            for (String t : tags.split(",")) if (!t.isBlank()) inv.getTags().add(t);
        }
    }

    public Optional<LoadedCase> load(String id) {
        String sql = "SELECT id, name, description, status, priority, analyst, classification, tags, created_at, updated_at, graph_json FROM investigations WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Investigation inv = new Investigation(rs.getString("id"), rs.getString("name"));
                setCommon(inv, rs);
                String json = rs.getString("graph_json");
                OrionGraph graph = json == null ? new OrionGraph() : deserializeGraph(json);
                if (json != null) {
                    // notas y fechas exactas via el documento JSON
                    JsonNode meta = JsonUtil.parse(json).path("investigation");
                    for (JsonNode note : meta.path("notes")) inv.getNotes().add(note.asText());
                    try {
                        inv.restoreTimestamps(Instant.parse(meta.path("createdAt").asText()),
                                Instant.parse(meta.path("updatedAt").asText()));
                    } catch (RuntimeException ignored) {
                        // documento antiguo sin fechas ISO: se conservan las actuales
                    }
                }
                return Optional.of(new LoadedCase(inv, graph));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo cargar el caso " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean delete(String id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM investigations WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public int count() {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM investigations");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            return -1;
        }
    }

    // ---- serializacion del grafo ----

    public String serializeCase(Investigation inv, OrionGraph graph) {
        ObjectNode root = JsonUtil.mapper().createObjectNode();
        root.set("investigation", JsonUtil.mapper().valueToTree(inv.snapshot()));
        ArrayNode entities = root.putArray("entities");
        for (var n : graph.getNodes()) {
            entities.add(JsonUtil.mapper().valueToTree(n.getEntity().snapshot()));
        }
        ArrayNode edges = root.putArray("edges");
        for (GraphEdge e : graph.getEdges()) {
            ObjectNode o = edges.addObject();
            o.put("from", e.getSource().getKey());
            o.put("to", e.getTarget().getKey());
            o.put("rel", e.getRelation().name());
            o.put("label", e.getLabel());
        }
        return root.toString();
    }

    public OrionGraph deserializeGraph(String json) {
        OrionGraph g = new OrionGraph();
        JsonNode root = JsonUtil.parse(json);
        // indice clave -> entidad para resolver aristas
        Map<String, OrionEntity> index = new LinkedHashMap<>();
        for (JsonNode en : root.path("entities")) {
            OrionEntity e = entityFrom(en);
            if (e == null) continue;
            index.put(e.key(), e);
            g.addEntity(e);
        }
        for (JsonNode ed : root.path("edges")) {
            OrionEntity from = index.get(JsonUtil.text(ed, "from"));
            OrionEntity to = index.get(JsonUtil.text(ed, "to"));
            RelationshipType rel = parseRel(JsonUtil.text(ed, "rel"));
            if (from != null && to != null) {
                g.addEdge(from, to, rel, JsonUtil.text(ed, "label"));
            }
        }
        return g;
    }

    private OrionEntity entityFrom(JsonNode n) {
        String type = JsonUtil.text(n, "type");
        String value = JsonUtil.text(n, "value");
        if (value.isEmpty()) return null;
        OrionEntity e = new OrionEntity(EntityType.fromString(type), value);
        e.setLabel(n.path("label").asText(value));
        e.setRiskScore(n.path("risk").asDouble(0));
        e.setTlp(Tlp.valueOf(n.path("tlp").asText("CLEAR")));
        JsonNode attrs = n.path("attributes");
        if (attrs.isObject()) {
            attrs.fields().forEachRemaining(f -> e.attr(f.getKey(), f.getValue().asText()));
        }
        JsonNode sources = n.path("sources");
        if (sources.isArray()) sources.forEach(s -> e.addSource(s.asText()));
        return e;
    }

    private RelationshipType parseRel(String s) {
        try {
            return RelationshipType.valueOf(s);
        } catch (Exception e) {
            return RelationshipType.RELATED_TO;
        }
    }
}
