package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * urlquery.net: reportes publicos de navegacion (sandbox) sobre un dominio,
 * IP o URL. La API exige clave (X-APIKEY); la busqueda es full-text y se
 * filtra por el host exacto del reporte.
 */
public class UrlquerySource implements SourceConnector {

    public static final String ID = "urlquery";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "urlquery.net (reportes)"; }

    @Override
    public String description() { return "Reportes públicos de navegación y alertas para dominios, IPs y URLs (clave requerida)."; }

    @Override
    public Category category() { return Category.COMMERCIAL; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "URLQUERY_KEY"; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.URLQUERY); }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = SourceRegistry.getInstance().apiKeyManager().getKey(ID);
        if (key == null || key.isBlank()) {
            return SourceResult.error(id(), "Clave de urlquery.net no configurada (Fuentes > Gestion de claves API)");
        }
        String value = q.value().trim();
        if (value.isEmpty()) return SourceResult.error(id(), "Valor vacio");
        try {
            String url = "https://api.urlquery.net/public/v1/search/reports/?query="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
                    + "&limit=25";
            String body = Http.get(url, Map.of("X-APIKEY", key));
            JsonNode root = JsonUtil.parse(body);
            JsonNode reports = root.path("reports");
            if (!reports.isArray() || reports.isEmpty()) {
                return SourceResult.ok(id(), "Sin reportes de urlquery para " + value);
            }

            SourceResult r = SourceResult.ok(id(), reports.size() + " reporte(s) de urlquery para " + value);
            String host = hostOf(value);
            int n = 0;
            for (JsonNode rep : reports) {
                if (n >= 6) break;
                String addr = JsonUtil.text(rep.path("url"), "addr");
                if (addr.isEmpty()) addr = JsonUtil.text(rep.path("final"), "url").isEmpty()
                        ? JsonUtil.text(rep, "addr") : JsonUtil.text(rep.path("final"), "url");
                if (addr.isEmpty()) continue;
                if (!relevant(value, host, rep, addr)) continue;
                n++;

                OrionEntity u = OrionEntity.of(EntityType.URL, addr)
                        .attr("reportId", JsonUtil.text(rep, "report_id"))
                        .attr("seen", truncate(JsonUtil.text(rep, "date"), 10))
                        .attr("title", JsonUtil.text(rep.path("final"), "title"))
                        .attr("alerts", alerts(rep))
                        .attr("source", id());
                u.addSource(id());
                u.setRiskScore(alerts(rep) > 0 ? 60 : 30);
                r.entity(u);
                r.edge(new EdgeDraft(value, addr, RelationshipType.RELATED_TO, "reporte urlquery"));
            }
            if (n == 0) {
                return SourceResult.ok(id(), "Sin reportes relevantes de urlquery para " + value);
            }
            r.raw("reports", reports.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "urlquery no disponible: " + e.getMessage());
        }
    }

    private static boolean relevant(String value, String host, JsonNode rep, String addr) {
        if (com.orion.core.util.NetUtil.isIpv4(value) || com.orion.core.util.NetUtil.isIpv6(value)) {
            return value.equals(JsonUtil.text(rep.path("ip"), "addr"));
        }
        String reportHost = hostOf(addr);
        if (reportHost.isEmpty()) return false;
        if (com.orion.core.util.NetUtil.isUrl(value)) return reportHost.equals(host);
        return reportHost.equals(host) || reportHost.endsWith("." + host);
    }

    private static int alerts(JsonNode rep) {
        JsonNode stats = rep.path("stats").path("alert_count");
        return stats.path("ids").asInt(0) + stats.path("urlquery").asInt(0) + stats.path("analyzer").asInt(0);
    }

    private static String hostOf(String value) {
        try {
            String v = value.contains("://") ? value : "http://" + value;
            String h = URI.create(v).getHost();
            return com.orion.core.util.NetUtil.normalizeDomain(h == null ? value : h);
        } catch (Exception e) {
            return com.orion.core.util.NetUtil.normalizeDomain(value);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
