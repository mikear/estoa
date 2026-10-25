package com.orion.sources;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CISA KEV (Known Exploited Vulnerabilities): catalogo de vulnerabilidades
 * con explotacion confirmada en circulacion. La comprobacion de un CVE
 * contra KEV cambia radicalmente la priorizacion del riesgo.
 */
public class CisaKevSource implements SourceConnector {

    public static final String ID = "cisa-kev";

    private final TtlCache<Map<String, JsonNode>> catalog = new TtlCache<>(Duration.ofHours(6));

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "CISA KEV (vulnerabilidades explotadas)"; }

    @Override
    public String description() { return "Catálogo CISA de vulnerabilidades con explotación confirmada en circulación."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.KEV_CHECK, Operation.CVE_INFO); }

    @Override
    public SourceResult query(SourceQuery q) {
        String cve = q.value().trim().toUpperCase();
        if (!com.orion.core.util.NetUtil.isCve(cve)) {
            return SourceResult.error(id(), "Identificador CVE no válido: " + q.value());
        }
        try {
            Map<String, JsonNode> map = catalog.get(this::download);
            JsonNode hit = map.get(cve);
            if (hit == null) {
                SourceResult r = SourceResult.ok(id(), cve + " no figura en el catálogo KEV");
                r.raw("kev", false);
                return r;
            }
            SourceResult r = SourceResult.ok(id(), cve + " está en el catálogo KEV: explotación confirmada");
            OrionEntity e = OrionEntity.of(EntityType.CVE, cve);
            e.addSource(id());
            e.attr("kev", true)
             .attr("kevAdded", JsonUtil.text(hit, "dateAdded"))
             .attr("dueDate", JsonUtil.text(hit, "dueDate"))
             .attr("knownRansomware", JsonUtil.text(hit, "knownRansomwareCampaignUse"))
             .attr("notes", JsonUtil.text(hit, "notes").length() > 200
                     ? JsonUtil.text(hit, "notes").substring(0, 200) + "..." : JsonUtil.text(hit, "notes"));
            e.setRiskScore(95);
            r.entity(e);
            r.raw("kev", true);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Catálogo KEV no disponible: " + e.getMessage());
        }
    }

    private Map<String, JsonNode> download() {
        Map<String, JsonNode> map = new HashMap<>();
        try {
            String body = Http.get("https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json");
            JsonNode root = JsonUtil.parse(body);
            JsonNode vulns = root.path("vulnerabilities");
            if (vulns.isArray()) {
                for (JsonNode v : vulns) {
                    map.put(JsonUtil.text(v, "cveID"), v);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
