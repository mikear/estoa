package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * VirusTotal (API v3, clave requerida): informe agregado de un observable
 * (IP, dominio, hash, URL) con detecciones de motores, comunidades y
 * atributos de red. La clave se obtiene de forma cifrada del ApiKeyManager.
 */
public class VirusTotalSource implements SourceConnector {

    public static final String ID = "virustotal";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "VirusTotal (comercial)"; }

    @Override
    public String description() { return "Informe agregado de reputación para IP, dominio, hash y URL (clave API requerida)."; }

    @Override
    public Category category() { return Category.COMMERCIAL; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "VIRUSTOTAL_KEY"; }

    @Override
    public Set<Operation> supports() {
        return Set.of(Operation.IP_REPUTATION, Operation.DOMAIN_THREATS, Operation.HASH_INFO, Operation.URL_REPUTATION, Operation.VT_REPORT);
    }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = SourceRegistry.getInstance().apiKeyManager().getKey(ID);
        if (key == null || key.isBlank()) {
            return SourceResult.error(id(), "Clave de VirusTotal no configurada (Fuentes > Gestión de claves API)");
        }
        String value = q.value().trim();
        String endpoint = endpointFor(value);
        if (endpoint == null) return SourceResult.error(id(), "Observable no soportado: " + value);
        try {
            String body = Http.get("https://www.virustotal.com/api/v3/" + endpoint,
                    Map.of("x-apikey", key));
            JsonNode root = JsonUtil.parse(body);
            JsonNode attrs = root.path("data").path("attributes");
            SourceResult r = SourceResult.ok(id(), "Informe VirusTotal para " + value);

            JsonNode stats = attrs.path("last_analysis_stats");
            int malicious = stats.path("malicious").asInt(0);
            int suspicious = stats.path("suspicious").asInt(0);
            EntityType t = com.orion.core.util.NetUtil.detectType(value);
            OrionEntity e = OrionEntity.of(t, value);
            e.addSource(id());
            e.attr("vtMalicious", malicious)
             .attr("vtSuspicious", suspicious)
             .attr("vtHarmless", stats.path("harmless").asInt(0))
             .attr("vtReputation", attrs.path("reputation").asInt(0));
            if (attrs.hasNonNull("as_owner")) e.attr("asOwner", JsonUtil.text(attrs, "as_owner"));
            if (attrs.hasNonNull("country")) e.attr("country", JsonUtil.text(attrs, "country"));
            if (t == EntityType.HASH && attrs.hasNonNull("type_description")) {
                e.attr("fileType", JsonUtil.text(attrs, "type_description"));
            }
            e.setRiskScore(Math.min(100, malicious * 8 + suspicious * 4));
            r.entity(e);
            r.raw("malicious", malicious);

            if (t == EntityType.HASH) {
                String popular = attrs.path("popular_threat_classification")
                        .path("suggested_threat_label").asText("");
                if (!popular.isEmpty()) {
                    String family = popular.split("\\.")[popular.split("\\.").length - 1]
                            .replace("trojan", "").trim();
                    if (!family.isBlank()) {
                        OrionEntity mal = OrionEntity.of(EntityType.MALWARE, family);
                        mal.setRiskScore(80);
                        mal.addSource(id());
                        r.entity(mal);
                        r.edge(EdgeDraft.of(value, family, RelationshipType.VARIANT_OF));
                    }
                }
            }
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "VirusTotal fallo: " + e.getMessage());
        }
    }

    private String endpointFor(String v) {
        if (com.orion.core.util.NetUtil.isIpv4(v) || com.orion.core.util.NetUtil.isIpv6(v)) return "ip_addresses/" + v;
        if (com.orion.core.util.NetUtil.isHash(v)) return "files/" + v.toLowerCase();
        if (com.orion.core.util.NetUtil.isDomain(v)) return "domains/" + com.orion.core.util.NetUtil.normalizeDomain(v);
        if (com.orion.core.util.NetUtil.isUrl(v)) {
            // VT identifica la URL por su base64url sin relleno
            return "urls/" + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(v.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return null;
    }
}
