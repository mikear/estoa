package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Hudson Rock Cavalier: credenciales de empleados y clientes comprometidas
 * por infostealers, agregadas por dominio. API publica sin clave.
 */
public class HudsonRockSource implements SourceConnector {

    public static final String ID = "hudsonrock";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Hudson Rock (infostealer)"; }

    @Override
    public String description() { return "Credenciales y URLs de acceso comprometidas por infostealers para el dominio."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.INFOSTEALER); }

    @Override
    public SourceResult query(SourceQuery q) {
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        if (!com.orion.core.util.NetUtil.isDomain(domain)) {
            return SourceResult.error(id(), "Dominio no válido: " + q.value());
        }
        try {
            String url = "https://cavalier.hudsonrock.com/api/json/v2/osint-tools/search-by-domain?domain="
                    + URLEncoder.encode(domain, StandardCharsets.UTF_8);
            JsonNode root = JsonUtil.parse(Http.get(url));
            long total = root.path("total").asLong(0);
            int employees = root.path("employees").asInt(0);
            int users = root.path("users").asInt(0);
            int thirdParties = root.path("third_parties").asInt(0);

            if (total <= 0) {
                return SourceResult.ok(id(), "Sin credenciales comprometidas para " + domain);
            }

            SourceResult r = SourceResult.ok(id(), "Hudson Rock: " + total
                    + " credenciales comprometidas (" + employees + " empleados, "
                    + users + " usuarios, " + thirdParties + " terceros)");

            OrionEntity incident = OrionEntity.of(EntityType.INCIDENT,
                            "Credenciales infostealer: " + domain)
                    .attr("total", total)
                    .attr("employees", employees)
                    .attr("users", users)
                    .attr("thirdParties", thirdParties)
                    .attr("source", id());
            incident.addSource(id());
            incident.setRiskScore(Math.min(90, 45 + Math.log10(Math.max(1, total)) * 12));
            r.entity(incident);
            r.edge(new EdgeDraft(domain, incident.getValue(), RelationshipType.OBSERVED_IN, "credenciales filtradas"));

            JsonNode data = root.path("data");
            JsonNode urls = data.path("all_urls");
            if (!urls.isArray() || urls.isEmpty()) urls = data.path("employees_urls");
            if (!urls.isArray() || urls.isEmpty()) urls = data.path("clients_urls");

            int n = 0;
            if (urls.isArray()) {
                for (JsonNode u : urls) {
                    if (n++ >= 12) break;
                    String value = JsonUtil.text(u, "url");
                    if (value.isEmpty()) continue;
                    OrionEntity ue = OrionEntity.of(EntityType.URL, value)
                            .attr("occurrence", u.path("occurrence").asInt(0))
                            .attr("exposureType", JsonUtil.text(u, "type"))
                            .attr("source", id());
                    ue.addSource(id());
                    ue.setRiskScore(55);
                    r.entity(ue);
                    r.edge(new EdgeDraft(domain, value, RelationshipType.RELATED_TO, "credenciales comprometidas"));
                }
            }
            r.raw("total", total);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Hudson Rock no disponible: " + e.getMessage());
        }
    }
}
