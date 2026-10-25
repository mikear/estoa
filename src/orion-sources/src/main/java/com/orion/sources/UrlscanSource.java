package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.orion.core.util.NetUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * urlscan.io (clave requerida): analisis de URL en sandbox con captura,
 * recursos solicitados y veredictos. Permite lanzar escaneos bajo demanda.
 */
public class UrlscanSource implements SourceConnector {

    public static final String ID = "urlscan";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "urlscan.io (comercial)"; }

    @Override
    public String description() { return "Escaneo de URLs bajo demanda con veredicto y recursos solicitados (clave requerida)."; }

    @Override
    public Category category() { return Category.COMMERCIAL; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "URLSCAN_KEY"; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.SCAN_INFO, Operation.URL_REPUTATION, Operation.DOMAIN_THREATS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = SourceRegistry.getInstance().apiKeyManager().getKey(ID);
        String url = q.value().trim();
        if (!NetUtil.isUrl(url)) {
            if (NetUtil.isDomain(url)) url = "https://" + NetUtil.normalizeDomain(url);
            else return SourceResult.error(id(), "Se requiere una URL o dominio: " + q.value());
        }
        try {
            // La busqueda es publica; la clave solo eleva el limite de peticiones.
            String domain = NetUtil.normalizeDomain(url);
            String query = URLEncoder.encode("domain:" + domain, StandardCharsets.UTF_8);
            Map<String, String> headers = key == null || key.isBlank() ? Map.of() : Map.of("API-Key", key);
            String search = Http.get("https://urlscan.io/api/v1/search/?q=" + query + "&size=100", headers);
            JsonNode root = JsonUtil.parse(search);
            JsonNode results = root.path("results");
            SourceResult r = SourceResult.ok(id(), results.size()
                    + (results.size() == 1 ? " escaneo previo de " : " escaneos previos de ") + domain);
            Set<String> seen = new HashSet<>();
            int n = 0;
            for (JsonNode res : results) {
                if (n++ >= 8) break;
                String pageUrl = JsonUtil.text(res.path("page"), "url");
                if (pageUrl.isBlank()) pageUrl = JsonUtil.text(res.path("task"), "url");
                if (pageUrl.isBlank()) continue;
                if (!NetUtil.isUrl(pageUrl)) pageUrl = "https://" + NetUtil.normalizeDomain(pageUrl);
                if (!seen.add(pageUrl.toLowerCase())) continue;
                OrionEntity e = OrionEntity.of(EntityType.URL, pageUrl)
                        .attr("scanId", JsonUtil.text(res, "_id"))
                        .attr("scanTime", JsonUtil.text(res, "task_time"))
                        .attr("verdict", JsonUtil.text(res.path("verdicts"), "overall"));
                e.addSource(id());
                r.entity(e);
                if (!pageUrl.equalsIgnoreCase(q.value().trim())) {
                    r.edge(new EdgeDraft(q.value().trim(), pageUrl, RelationshipType.RELATED_TO, "escaneo urlscan"));
                }
            }
            r.raw("scans", results.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "urlscan.io fallo: " + e.getMessage());
        }
    }
}
