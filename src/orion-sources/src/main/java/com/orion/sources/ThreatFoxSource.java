package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * ThreatFox (AbuseCH): base colaborativa de IOCs asociados a malware y APTs.
 * Permite buscar por IP, dominio, URL o hash y devuelve el malware asociado
 * junto con el nivel de confianza reportado.
 */
public class ThreatFoxSource implements SourceConnector {

    public static final String ID = "threatfox";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "ThreatFox (AbuseCH)"; }

    @Override
    public String description() { return "Búsqueda de IOCs maliciosos (IP, dominio, URL, hash) con malware asociado y confianza."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "ABUSECH_KEY"; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.IP_REPUTATION, Operation.DOMAIN_THREATS, Operation.URL_REPUTATION, Operation.HASH_INFO); }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = AbuseChKey.resolve(ID);
        if (key == null) return SourceResult.error(id(), AbuseChKey.missingMessage());
        String ioc = q.value().trim();
        if (ioc.isEmpty()) return SourceResult.error(id(), "IOC vacio");
        try {
            String body = Http.postJson("https://threatfox-api.abuse.ch/api/v1/",
                    "{\"query\":\"search_ioc\",\"search_term\":\"" + ioc + "\"}",
                    AbuseChKey.headers(key));
            JsonNode root = JsonUtil.parse(body);
            if (!"query_success".equals(JsonUtil.text(root, "query_status"))) {
                return SourceResult.ok(id(), "Sin coincidencias en ThreatFox para " + ioc);
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return SourceResult.ok(id(), "Sin coincidencias en ThreatFox para " + ioc);
            }
            SourceResult r = SourceResult.ok(id(), data.size() + " coincidencias en ThreatFox");
            for (JsonNode d : data) {
                String malware = JsonUtil.text(d, "malware_printable");
                String conf = JsonUtil.text(d, "confidence_level");
                double risk = switch (conf) {
                    case "100" -> 85; case "75" -> 65; case "50" -> 45; default -> 30;
                };
                OrionEntity m = OrionEntity.of(EntityType.MALWARE, malware)
                        .attr("threatFoxConfidence", conf)
                        .attr("firstSeen", JsonUtil.text(d, "first_seen_utc"))
                        .attr("lastSeen", JsonUtil.text(d, "last_seen_utc"));
                m.setRiskScore(risk);
                m.addSource(id());
                r.entity(m);
                r.edge(EdgeDraft.of(ioc, malware, RelationshipType.RELATED_TO));

                String actor = JsonUtil.text(d, "threat_type");
                if (!actor.isEmpty()) {
                    // etiqueta del tipo de amenaza (botnet, c2, payload delivery...)
                }
            }
            r.raw("hits", data.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "ThreatFox no disponible: " + e.getMessage());
        }
    }
}
