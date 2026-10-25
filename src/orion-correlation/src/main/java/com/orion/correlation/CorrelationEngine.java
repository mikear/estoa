package com.orion.correlation;

import com.orion.graph.OrionGraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de correlacion: ejecuta las reglas habilitadas sobre el grafo del
 * caso y sintetiza hallazgos en hipotesis de investigacion accionables.
 */
public class CorrelationEngine {

    /** Resultado de una corrida del motor. */
    public record CorrelationReport(List<Finding> findings, List<Hypothesis> hypotheses) {
        public int findingsByRule(String ruleId) {
            return (int) findings.stream().filter(f -> f.ruleId().equals(ruleId)).count();
        }
    }

    private final Map<String, CorrelationRule> rules = new LinkedHashMap<>();

    public CorrelationEngine() {
        StandardRules.all().forEach(r -> rules.put(r.id(), r));
    }

    public void setEnabled(String ruleId, boolean enabled) {
        CorrelationRule r = rules.get(ruleId);
        if (r != null) r.setEnabled(enabled);
    }

    public List<CorrelationRule> getRules() { return new ArrayList<>(rules.values()); }

    public CorrelationReport run(OrionGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (CorrelationRule r : rules.values()) {
            if (!r.isEnabled()) continue;
            try {
                findings.addAll(r.evaluate(graph));
            } catch (Exception ignored) {
                // una regla defectuosa no debe tumbar la corrida
            }
        }
        return new CorrelationReport(findings, synthesize(findings));
    }

    /** Convierte los hallazgos de confianza suficiente en hipotesis. */
    public List<Hypothesis> synthesize(List<Finding> findings) {
        List<Hypothesis> out = new ArrayList<>();
        for (Finding f : findings) {
            if (f.confidence() < 50) continue;
            String statement = "HP: " + f.description() + " (confianza " + Math.round(f.confidence()) + "%)."
                    + " Acción sugerida: " + suggestedAction(f.ruleId());
            out.add(new Hypothesis(statement, f.confidence(), f.ruleId(), f.entityKeys()));
        }
        return out;
    }

    private String suggestedAction(String ruleId) {
        return switch (ruleId) {
            case "shared-asn" -> "revisar histórico del ASN y valorar el bloqueo del bloque de red";
            case "shared-cert" -> "pivotar por el certificado en Censys/crt.sh para ampliar infraestructura";
            case "registrar-overlap" -> "solicitar abuso al registrador y vigilar altas futuras";
            case "multi-source" -> "promover a IOC de alta confianza y desplegar bloqueo";
            case "hash-lineage" -> "actualizar firmas YARA de la familia";
            case "ttp-overlap" -> "fusionar actores en el caso si la evidencia lo sostiene";
            case "domain-similarity" -> "registrar variantes defensivas y alertar a usuarios";
            case "geo-colocation" -> "bloqueo geográfico ponderado y monitorización del rango";
            default -> "revisión manual";
        };
    }
}
