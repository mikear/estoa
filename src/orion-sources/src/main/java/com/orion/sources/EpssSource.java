package com.orion.sources;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * EPSS (Exploit Prediction Scoring System, FIRST): probabilidad observada de
 * que una CVE concreta sea explotada en los proximos 30 dias. Es la senal que
 * falta para priorizar vulnerabilidades mas alla del CVSS.
 */
public class EpssSource implements SourceConnector {

    public static final String ID = "epss";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "EPSS (FIRST)"; }

    @Override
    public String description() { return "Probabilidad de explotación y percentil de una CVE durante los próximos 30 días."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.EPSS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String cve = q.value().trim().toUpperCase();
        if (!com.orion.core.util.NetUtil.isCve(cve)) {
            return SourceResult.error(id(), "Identificador CVE no válido: " + q.value());
        }
        try {
            String body = Http.get("https://api.first.org/data/v1/epss?cve=" + cve);
            JsonNode root = JsonUtil.parse(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return SourceResult.ok(id(), "EPSS sin datos para " + cve);
            }
            JsonNode d = data.get(0);
            double epss = parse(d.path("epss").asText("0"));
            double percentile = parse(d.path("percentile").asText("0"));
            String date = JsonUtil.text(d, "date");

            OrionEntity e = OrionEntity.of(EntityType.CVE, cve);
            e.addSource(id());
            e.attr("epss", epss)
             .attr("epssPercentile", percentile)
             .attr("epssDate", date);
            // La probabilidad es 0..1; se refleja en la escala 0..100 del riesgo.
            e.setRiskScore(Math.min(100, Math.round(epss * 100)));
            SourceResult r = SourceResult.ok(id(), String.format(
                    "%s EPSS %.3f (percentil %.1f%%)", cve, epss, percentile * 100));
            r.entity(e);
            r.raw("epss", epss).raw("percentile", percentile);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "EPSS no disponible: " + e.getMessage());
        }
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
