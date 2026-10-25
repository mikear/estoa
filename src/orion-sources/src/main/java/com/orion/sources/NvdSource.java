package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * NVD (National Vulnerability Database): detalle canonico de vulnerabilidades
 * CVE con puntuacion CVSS y referencias. Sin clave el limite es de
 * aproximadamente 5 solicitudes cada 30 segundos.
 */
public class NvdSource implements SourceConnector {

    public static final String ID = "nvd";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "NVD (base nacional de vulnerabilidades)"; }

    @Override
    public String description() { return "Detalle CVE: CVSS, vector de acceso, descripciones y referencias (NIST)."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.CVE_INFO); }

    @Override
    public SourceResult query(SourceQuery q) {
        String cve = q.value().trim().toUpperCase();
        if (!com.orion.core.util.NetUtil.isCve(cve)) {
            return SourceResult.error(id(), "Identificador CVE no válido: " + q.value());
        }
        try {
            String body = Http.get("https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=" + cve);
            JsonNode root = JsonUtil.parse(body);
            JsonNode vulns = root.path("vulnerabilities");
            if (!vulns.isArray() || vulns.isEmpty()) {
                return SourceResult.ok(id(), "CVE no encontrado en NVD: " + cve);
            }
            JsonNode v = vulns.get(0).path("cve");
            JsonNode metrics = v.path("metrics");
            double cvss = 0;
            String severity = "UNKNOWN", vector = "";
            if (metrics.path("cvssMetricV31").isArray() && !metrics.path("cvssMetricV31").isEmpty()) {
                JsonNode m = metrics.path("cvssMetricV31").get(0).path("cvssData");
                cvss = m.path("baseScore").asDouble(0);
                severity = JsonUtil.text(m, "baseSeverity");
                vector = JsonUtil.text(m, "vectorString");
            } else if (metrics.path("cvssMetricV30").isArray() && !metrics.path("cvssMetricV30").isEmpty()) {
                JsonNode m = metrics.path("cvssMetricV30").get(0).path("cvssData");
                cvss = m.path("baseScore").asDouble(0);
                severity = JsonUtil.text(m, "baseSeverity");
                vector = JsonUtil.text(m, "vectorString");
            }
            SourceResult r = SourceResult.ok(id(), cve + " CVSS " + cvss + " " + severity);
            OrionEntity e = OrionEntity.of(EntityType.CVE, cve);
            e.addSource(id());
            e.attr("cvss", cvss).attr("severity", severity).attr("vector", vector)
             .attr("published", JsonUtil.text(v, "published"))
             .attr("modified", JsonUtil.text(v, "lastModified"))
             .attr("status", JsonUtil.text(v, "vulnStatus"));
            if (v.path("descriptions").isArray() && !v.path("descriptions").isEmpty()) {
                e.attr("description", JsonUtil.text(v.path("descriptions").get(0), "value"));
            }
            if (v.path("weaknesses").isArray() && !v.path("weaknesses").isEmpty()) {
                e.attr("cwe", JsonUtil.text(v.path("weaknesses").get(0).path("description").path(0), "value"));
            }
            e.setRiskScore(Math.min(100, cvss * 10));
            r.entity(e);
            r.raw("cvss", cvss);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "NVD no disponible (posible límite de tasa): " + e.getMessage());
        }
    }
}
