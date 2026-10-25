package com.orion.reports;

import com.orion.correlation.Finding;
import com.orion.correlation.Hypothesis;
import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.graph.OrionGraph;
import com.orion.intel.AttackMatrixService;
import com.orion.intel.RiskScoringService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Datos consolidados para generar un informe: caso, grafo, riesgo, matriz
 * ATT&CK y resultados de correlacion. Inmutable.
 */
public record ReportModel(
        Investigation investigation,
        OrionGraph graph,
        RiskScoringService.CaseRisk risk,
        List<AttackMatrixService.ActorProfile> actors,
        List<AttackMatrixService.TacticRow> matrix,
        List<Finding> findings,
        List<Hypothesis> hypotheses,
        String generatedBy,
        Instant generatedAt) {

    public static ReportModel empty(Investigation inv) {
        return new ReportModel(inv, new OrionGraph(), new RiskScoringService.CaseRisk(0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), "Estoa 1.0", Instant.now());
    }

    /** IOCs priorizados: entidades con riesgo >= 40 ordenadas por riesgo. */
    public List<OrionEntity> iocs() {
        return graph.getNodes().stream()
                .map(n -> n.getEntity())
                .filter(e -> e.getRiskScore() >= 40 || e.getSources().size() >= 2)
                .sorted(Comparator.comparingDouble(OrionEntity::getRiskScore).reversed())
                .toList();
    }

    /** Entidades ordenadas por tipo y riesgo para el inventario. */
    public List<OrionEntity> inventory() {
        return graph.getNodes().stream()
                .map(n -> n.getEntity())
                .sorted(Comparator.comparing((OrionEntity e) -> e.getType().label())
                        .thenComparing(Comparator.comparingDouble(OrionEntity::getRiskScore).reversed()))
                .toList();
    }

    public long countByType(EntityType t) {
        return graph.getNodes().stream().filter(n -> n.getEntity().getType() == t).count();
    }

    public String iocTableTitle() {
        return "Indicadores de compromiso (" + iocs().size() + ")";
    }
}
