package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HIBP (Have I Been Pwned): catalogo publico de filtraciones de datos. Se
 * descarga una vez cada 12 horas (~1,1 MB) y se filtra por el dominio
 * investigado, convirtiendo cada brecha en un incidente del grafo.
 */
public class HibpSource implements SourceConnector {

    public static final String ID = "hibp";

    private final TtlCache<List<JsonNode>> catalog = new TtlCache<>(Duration.ofHours(12));

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "HIBP (filtraciones de datos)"; }

    @Override
    public String description() { return "Brechas de datos públicas que afectan al dominio, con fecha, volumen y tipos de datos."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.BREACHES); }

    @Override
    public SourceResult query(SourceQuery q) {
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        if (!com.orion.core.util.NetUtil.isDomain(domain)) {
            return SourceResult.error(id(), "Dominio no válido: " + q.value());
        }
        try {
            List<JsonNode> breaches = catalog.get(this::download);
            List<JsonNode> hits = new ArrayList<>();
            for (JsonNode b : breaches) {
                String bd = com.orion.core.util.NetUtil.normalizeDomain(JsonUtil.text(b, "Domain"));
                if (bd.isEmpty()) continue;
                if (bd.equals(domain) || domain.endsWith("." + bd) || bd.endsWith("." + domain)) {
                    hits.add(b);
                }
            }
            if (hits.isEmpty()) {
                return SourceResult.ok(id(), "Sin filtraciones conocidas para " + domain);
            }

            SourceResult r = SourceResult.ok(id(), hits.size() + " filtración(es) para " + domain);
            int n = 0;
            for (JsonNode b : hits) {
                if (n++ >= 10) break;
                String title = JsonUtil.text(b, "Title");
                if (title.isEmpty()) title = JsonUtil.text(b, "Name");
                OrionEntity incident = OrionEntity.of(EntityType.INCIDENT, title)
                        .attr("breachDomain", JsonUtil.text(b, "Domain"))
                        .attr("breachDate", JsonUtil.text(b, "BreachDate"))
                        .attr("pwnCount", b.path("PwnCount").asLong(0))
                        .attr("dataClasses", b.path("DataClasses").toString())
                        .attr("verified", b.path("IsVerified").asBoolean(false))
                        .attr("sensitive", b.path("IsSensitive").asBoolean(false))
                        .attr("malware", b.path("IsMalware").asBoolean(false))
                        .attr("description", truncate(JsonUtil.text(b, "Description"), 240))
                        .attr("source", id());
                incident.addSource(id());
                incident.setRiskScore(breachRisk(b));
                r.entity(incident);
                r.edge(new EdgeDraft(domain, title, RelationshipType.OBSERVED_IN, "filtración"));
            }
            r.raw("breaches", hits.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "HIBP no disponible: " + e.getMessage());
        }
    }

    private List<JsonNode> download() {
        List<JsonNode> out = new ArrayList<>();
        try {
            JsonNode root = JsonUtil.parse(Http.get("https://haveibeenpwned.com/api/v3/breaches"));
            if (root.isArray()) root.forEach(out::add);
        } catch (Exception ignored) {
            // se devuelve el catalogo vacio; la transformacion informa sin filtraciones
        }
        return out;
    }

    private static double breachRisk(JsonNode b) {
        long pwn = b.path("PwnCount").asLong(0);
        double risk = 35 + Math.min(35, Math.log10(Math.max(1, pwn)) * 8);
        String classes = b.path("DataClasses").toString().toLowerCase();
        if (classes.contains("password")) risk += 15;
        if (b.path("IsSensitive").asBoolean(false)) risk += 10;
        return Math.min(90, risk);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
