package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * URLHaus (AbuseCH): repositorio de URLs de distribucion de malware
 * (payload delivery) verificado por la comunidad.
 */
public class UrlHausSource implements SourceConnector {

    public static final String ID = "urlhaus";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "URLHaus (AbuseCH)"; }

    @Override
    public String description() { return "URLs maliciosas de distribución de malware verificadas, consultables por equipo o URL."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "ABUSECH_KEY"; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.URL_REPUTATION, Operation.IP_REPUTATION, Operation.DOMAIN_THREATS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = AbuseChKey.resolve(ID);
        if (key == null) return SourceResult.error(id(), AbuseChKey.missingMessage());
        String value = q.value().trim();
        if (value.isEmpty()) return SourceResult.error(id(), "Valor vacio");
        try {
            String host = value;
            if (com.orion.core.util.NetUtil.isUrl(value)) {
                host = com.orion.core.util.NetUtil.normalizeDomain(value);
            }
            String body = Http.postForm("https://urlhaus-api.abuse.ch/v1/host/",
                    "host=" + java.net.URLEncoder.encode(host, java.nio.charset.StandardCharsets.UTF_8),
                    AbuseChKey.headers(key));
            JsonNode root = JsonUtil.parse(body);
            if (!"online".equals(JsonUtil.text(root, "query_status"))) {
                return SourceResult.ok(id(), "Sin resultados URLHaus");
            }
            int count = root.path("url_count").asInt(root.path("urls").size());
            SourceResult r = SourceResult.ok(id(), count + " URLs maliciosas alojadas en " + host);
            JsonNode urls = root.path("urls");
            if (urls.isArray()) {
                int n = 0;
                for (JsonNode u : urls) {
                    if (n++ >= 25) break; // limitar expansion del grafo
                    String url = JsonUtil.text(u, "url");
                    if (url.isEmpty()) continue;
                    OrionEntity e = OrionEntity.of(EntityType.URL, url)
                            .attr("status", JsonUtil.text(u, "url_status"))
                            .attr("threat", JsonUtil.text(u, "threat"))
                            .attr("firstSeen", JsonUtil.text(u, "date_added"));
                    e.setRiskScore(70);
                    e.addSource(id());
                    r.entity(e);
                    if (!url.equalsIgnoreCase(value)) {
                        r.edge(new EdgeDraft(value, url, RelationshipType.HOSTS, "aloja URL maliciosa"));
                    }
                }
            }
            r.raw("urlCount", count);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "URLHaus no disponible: " + e.getMessage());
        }
    }
}
