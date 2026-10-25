package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MITRE ATT&CK: subconjunto embebido del bundle STIX Enterprise (tacticas
 * completas, tecnicas y grupos seleccionados) disponible sin conexion. Las
 * consultas devuelven tecnicas, tacticas y grupos relacionados para construir
 * la matriz de cobertura del caso.
 */
public class MitreAttackSource implements SourceConnector {

    public static final String ID = "mitre-attack";

    private final Map<String, JsonNode> techniques = new LinkedHashMap<>();
    private final Map<String, JsonNode> tactics = new LinkedHashMap<>();
    private final Map<String, JsonNode> groups = new LinkedHashMap<>();
    private String version = "";

    public MitreAttackSource() {
        try (InputStream in = getClass().getResourceAsStream("/attack/attack.json")) {
            if (in == null) return;
            JsonNode root = JsonUtil.mapper().readTree(in);
            version = JsonUtil.text(root, "version");
            for (JsonNode t : root.path("tactics")) tactics.put(JsonUtil.text(t, "id"), t);
            for (JsonNode t : root.path("techniques")) techniques.put(JsonUtil.text(t, "id"), t);
            for (JsonNode g : root.path("groups")) {
                groups.put(JsonUtil.text(g, "name").toLowerCase(), g);
                for (JsonNode alias : g.path("aliases")) {
                    groups.putIfAbsent(alias.asText().toLowerCase(), g);
                }
            }
        } catch (Exception e) {
            // sin bundle embebido el conector queda vacio
        }
    }

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "MITRE ATT&CK (bundle embebido)"; }

    @Override
    public String description() { return "Matriz ATT&CK Enterprise: tácticas, técnicas y grupos con perfil TTP embebido."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() {
        return Set.of(Operation.TECHNIQUE_INFO, Operation.ACTOR_INFO, Operation.GROUP_INFO);
    }

    @Override
    public SourceResult query(SourceQuery q) {
        return switch (q.operation()) {
            case TECHNIQUE_INFO -> technique(q.value());
            case ACTOR_INFO, GROUP_INFO -> group(q.value());
            default -> SourceResult.error(id(), "Operación no soportada");
        };
    }

    private SourceResult technique(String techId) {
        String id = techId.trim().toUpperCase();
        JsonNode t = techniques.get(id);
        if (t == null) return SourceResult.ok(id(), "Técnica no encontrada en el bundle embebido: " + id);
        SourceResult r = SourceResult.ok(id(), id + " - " + JsonUtil.text(t, "name"));
        OrionEntity e = OrionEntity.of(EntityType.ATTCK_TECHNIQUE, id);
        e.addSource(id());
        e.attr("name", JsonUtil.text(t, "name"))
         .attr("description", JsonUtil.text(t, "desc_es"))
         .attr("tactic", JsonUtil.text(t, "tactic"));
        r.entity(e);

        JsonNode tac = tactics.get(JsonUtil.text(t, "tactic"));
        if (tac != null) {
            OrionEntity te = OrionEntity.of(EntityType.ATTCK_TACTIC, JsonUtil.text(t, "tactic"))
                    .attr("name", JsonUtil.text(tac, "name"))
                    .attr("name_es", JsonUtil.text(tac, "name_es"));
            te.addSource(id());
            r.entity(te);
            r.edge(EdgeDraft.of(id, JsonUtil.text(t, "tactic"), RelationshipType.MEMBER_OF));
        }
        for (JsonNode g : groups.values()) {
            for (JsonNode tech : g.path("techniques")) {
                if (tech.asText().equals(id)) {
                    OrionEntity actor = groupEntity(g);
                    r.entity(actor);
                    r.edge(EdgeDraft.of(JsonUtil.text(g, "name"), id, RelationshipType.USES_TECHNIQUE));
                }
            }
        }
        return r;
    }

    private SourceResult group(String name) {
        JsonNode g = groups.get(name.trim().toLowerCase());
        if (g == null) return SourceResult.ok(id(), "Grupo no encontrado: " + name);
        SourceResult r = SourceResult.ok(id(), "Perfil de " + JsonUtil.text(g, "name"));
        OrionEntity actor = groupEntity(g);
        r.entity(actor);
        for (JsonNode tech : g.path("techniques")) {
            String tid = tech.asText();
            JsonNode t = techniques.get(tid);
            if (t != null) {
                OrionEntity te = OrionEntity.of(EntityType.ATTCK_TECHNIQUE, tid)
                        .attr("name", JsonUtil.text(t, "name"))
                        .attr("tactic", JsonUtil.text(t, "tactic"));
                te.addSource(id());
                r.entity(te);
                r.edge(EdgeDraft.of(JsonUtil.text(g, "name"), tid, RelationshipType.USES_TECHNIQUE));
            }
        }
        return r;
    }

    private OrionEntity groupEntity(JsonNode g) {
        OrionEntity actor = OrionEntity.of(EntityType.APT, JsonUtil.text(g, "name"))
                .attr("aliases", g.path("aliases").toString().replace("[", "").replace("]", "").replace("\"", ""))
                .attr("origin", JsonUtil.text(g, "origin"))
                .attr("description", JsonUtil.text(g, "desc_es"))
                .attr("attckId", JsonUtil.text(g, "id"));
        actor.setRiskScore(70);
        actor.addSource(id());
        return actor;
    }

    /** Matriz completa para la vista ATT&CK: tactica -> tecnicas. */
    public Map<String, List<JsonNode>> matrix() {
        Map<String, List<JsonNode>> m = new LinkedHashMap<>();
        for (String tid : tactics.keySet()) m.put(tid, new ArrayList<>());
        techniques.values().forEach(t -> {
            List<JsonNode> list = m.get(JsonUtil.text(t, "tactic"));
            if (list != null) list.add(t);
        });
        return m;
    }

    public Map<String, JsonNode> getTechniques() { return techniques; }
    public Map<String, JsonNode> getTactics() { return tactics; }
    public Map<String, JsonNode> getGroups() { return groups; }
    public String version() { return version; }
}
