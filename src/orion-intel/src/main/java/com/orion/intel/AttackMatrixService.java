package com.orion.intel;

import com.orion.core.model.EntityType;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.sources.MitreAttackSource;
import com.orion.sources.SourcesBootstrap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de matriz MITRE ATT&CK: calcula la cobertura de tacticas y
 * tecnicas observadas en el caso y sugiere el perfil del actor.
 */
public class AttackMatrixService {

    /** Fila de tactica para la vista de matriz. */
    public record TacticRow(String tacticId, String tacticName, String tacticNameEs,
                            List<TechniqueCell> techniques, int observedCount) {}

    /** Celda de tecnica: observada si existe una entidad ATTCK_TECHNIQUE igual. */
    public record TechniqueCell(String id, String name, boolean observed, int entitiesUsing) {}

    private final MitreAttackSource attack;

    public AttackMatrixService() {
        this(SourcesBootstrap.attack());
    }

    public AttackMatrixService(MitreAttackSource attack) {
        this.attack = attack;
    }

    public List<TacticRow> matrix(OrionGraph g) {
        Map<String, Integer> observed = new LinkedHashMap<>();
        for (GraphNode n : g.getNodes()) {
            if (n.getEntity().getType() == EntityType.ATTCK_TECHNIQUE) {
                observed.merge(n.getEntity().getValue().toUpperCase(), 1, Integer::sum);
            }
        }
        List<TacticRow> rows = new ArrayList<>();
        for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> tac : attack.getTactics().entrySet()) {
            List<TechniqueCell> cells = new ArrayList<>();
            int observedCount = 0;
            for (var t : attack.getTechniques().values()) {
                if (!t.path("tactic").asText("").equals(tac.getKey())) continue;
                String id = t.path("id").asText();
                int uses = observed.getOrDefault(id, 0);
                boolean isObserved = uses > 0;
                if (isObserved) observedCount++;
                cells.add(new TechniqueCell(id, t.path("name").asText(), isObserved, uses));
            }
            if (!cells.isEmpty()) {
                rows.add(new TacticRow(tac.getKey(),
                        tac.getValue().path("name").asText(),
                        tac.getValue().path("name_es").asText(),
                        cells, observedCount));
            }
        }
        return rows;
    }

    /** Cobertura: porcentaje de tecnicas observadas sobre el total embebido. */
    public double coverage(OrionGraph g) {
        int total = attack.getTechniques().size();
        if (total == 0) return 0;
        long observed = g.getNodes().stream()
                .filter(n -> n.getEntity().getType() == EntityType.ATTCK_TECHNIQUE).count();
        return Math.min(100, observed * 100.0 / total);
    }

    /** Numero de tecnicas del subconjunto embebido (no todo ATT&CK Enterprise). */
    public int techniqueCount() { return attack.getTechniques().size(); }

    /** Version del bundle embebido, p. ej. enterprise-attack.15.1-subset. */
    public String bundleVersion() { return attack.version(); }

    /** Actores del caso con numero de tecnicas compartidas. */
    public List<ActorProfile> actorProfiles(OrionGraph g) {
        List<ActorProfile> out = new ArrayList<>();
        for (GraphNode n : g.getNodes()) {
            if (n.getEntity().getType() != EntityType.APT && n.getEntity().getType() != EntityType.THREAT_ACTOR) continue;
            long techniques = g.neighbors(n.getKey()).stream()
                    .filter(t -> t.getEntity().getType() == EntityType.ATTCK_TECHNIQUE).count();
            out.add(new ActorProfile(n.getEntity().getValue(),
                    n.getEntity().attrStr("aliases", ""),
                    n.getEntity().attrStr("origin", ""),
                    n.getEntity().attrStr("description", ""),
                    (int) techniques, n.getEntity().getRiskScore()));
        }
        out.sort((a, b) -> Integer.compare(b.techniques(), a.techniques()));
        return out;
    }

    public record ActorProfile(String name, String aliases, String origin, String description,
                               int techniques, double risk) {}
}
