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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
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

    public PersistenceService(DataSource ds) {
        this.ds = ds;
        try (Connection c = ds.getConnection()) {
            Schema.init(c);
            restrictH2File(c);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inicializar el esquema: " + e.getMessage(), e);
        }
    }

    /** Deja el fichero H2 solo para el usuario; en Windows los permisos del perfil ya aplican. */
    private static void restrictH2File(Connection c) {
        try {
            String url = c.getMetaData().getURL();
            if (url == null || !url.startsWith("jdbc:h2:") || url.contains(":mem:")) return;
            Path db = Path.of(url.substring("jdbc:h2:".length()).split(";")[0] + ".mv.db");
            if (!Files.getFileStore(db.getParent()).supportsFileAttributeView(PosixFileAttributeView.class)) return;
            Files.setPosixFilePermissions(db, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // sin permisos POSIX o fichero aun sin crear: no hay nada que restringir
        }
    }

    public void save(Investigation inv, OrionGraph graph) {
        String json = serializeCase(inv, graph);
        boolean nuevo = inv.getVersion() == 0;
        String sql = nuevo ? """
                INSERT INTO investigations (id, name, description, status, priority, analyst, classification,
                                            tags, created_at, updated_at, version, graph_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """ : """
                UPDATE investigations SET name=?, description=?, status=?, priority=?, analyst=?,
                  classification=?, tags=?, created_at=?, updated_at=?, version=?, graph_json=?
                WHERE id=? AND version=?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (nuevo) ps.setString(i++, inv.getId());
            ps.setString(i++, inv.getName());
            ps.setString(i++, inv.getDescription());
            ps.setString(i++, inv.getStatus().name());
            ps.setString(i++, inv.getPriority().name());
            ps.setString(i++, inv.getAnalyst());
            ps.setString(i++, inv.getClassification());
            ps.setString(i++, String.join(",", inv.getTags()));
            ps.setTimestamp(i++, Timestamp(inv.getCreatedAt()));
            ps.setTimestamp(i++, Timestamp(inv.getUpdatedAt()));
            ps.setInt(i++, inv.getVersion() + 1);
            ps.setString(i++, json);
            if (!nuevo) {
                ps.setString(i++, inv.getId());
                ps.setInt(i, inv.getVersion());
            }
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException(
                        "El caso fue modificado por otro analista. Vuelve a abrirlo antes de guardar.");
            }
            inv.setVersion(inv.getVersion() + 1);
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
        String sql = "SELECT id, name, description, status, priority, analyst, classification, tags, created_at, updated_at, version, graph_json FROM investigations WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Investigation inv = new Investigation(rs.getString("id"), rs.getString("name"));
                setCommon(inv, rs);
                // un caso guardado antes de existir la columna tiene versión 0: cuenta como 1
                inv.setVersion(Math.max(1, rs.getInt("version")));
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
