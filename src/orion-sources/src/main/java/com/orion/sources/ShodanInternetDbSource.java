package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Shodan InternetDB: exposicion publica de una IP sin clave de API. Devuelve
 * puertos abiertos, nombres de host, etiquetas, productos (CPE) y las CVE
 * conocidas del servicio. Complementa a {@link ShodanSource}, que exige clave.
 */
public class ShodanInternetDbSource implements SourceConnector {

    public static final String ID = "shodan-internetdb";

    private static final int MAX_PORTS = 30;
    private static final int MAX_HOSTNAMES = 15;
    private static final int MAX_CVES = 30;
    private static final int MAX_CPES = 15;

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Shodan InternetDB (sin clave)"; }

    @Override
    public String description() { return "Puertos, hostnames, etiquetas, productos y CVE expuestos por una IP, sin clave de API."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.INTERNET_DB); }

    @Override
    public SourceResult query(SourceQuery q) {
        String ip = q.value().trim();
        boolean v4 = com.orion.core.util.NetUtil.isIpv4(ip);
        if (!v4 && !com.orion.core.util.NetUtil.isIpv6(ip)) {
            return SourceResult.error(id(), "Se requiere una IP válida: " + q.value());
        }
        try {
            String body = Http.get("https://internetdb.shodan.io/" + ip);
            JsonNode root = JsonUtil.parse(body);
            if (!root.path("detail").asText("").isEmpty() && root.path("ports").isMissingNode()) {
                return SourceResult.ok(id(), "Sin exposición pública registrada para " + ip);
            }

            JsonNode ports = root.path("ports");
            JsonNode hostnames = root.path("hostnames");
            JsonNode tags = root.path("tags");
            JsonNode vulns = root.path("vulns");
            JsonNode cpes = root.path("cpes");

            EntityType type = v4 ? EntityType.IPV4 : EntityType.IPV6;
            OrionEntity host = OrionEntity.of(type, ip);
            host.addSource(id());
            host.attr("internetDbPorts", ports.size())
                .attr("internetDbHostnames", join(hostnames))
                .attr("internetDbTags", join(tags))
                .attr("internetDbVulns", vulns.size())
                .attr("internetDbCpes", cpes.size());

            double risk = 20 + Math.min(30, ports.size() * 3) + Math.min(30, vulns.size() / 10.0);
            for (JsonNode tag : tags) {
                String t = tag.asText("").toLowerCase();
                if (t.contains("malware") || t.contains("c2") || t.contains("compromised")
                        || t.contains("honeypot") || t.contains("tor")) {
                    risk = Math.max(risk, 80);
                }
            }
            host.setRiskScore(Math.min(90, risk));

            SourceResult r = SourceResult.ok(id(), "Exposición de " + ip + ": "
                    + ports.size() + " puertos, " + vulns.size() + " CVE, "
                    + hostnames.size() + " hostnames");
            r.entity(host);

            int n = 0;
            for (JsonNode port : ports) {
                if (n++ >= MAX_PORTS) break;
                String value = String.valueOf(port.asInt());
                OrionEntity pe = OrionEntity.of(EntityType.PORT, value)
                        .attr("protocol", "tcp")
                        .attr("source", id());
                pe.addSource(id());
                r.entity(pe);
                r.edge(new EdgeDraft(ip, value, RelationshipType.HOSTS, "expone puerto"));
            }

            n = 0;
            for (JsonNode hn : hostnames) {
                if (n++ >= MAX_HOSTNAMES) break;
                String name = com.orion.core.util.NetUtil.normalizeDomain(hn.asText(""));
                if (name.isEmpty()) continue;
                OrionEntity dom = OrionEntity.of(EntityType.DOMAIN, name).attr("source", id());
                dom.addSource(id());
                r.entity(dom);
                r.edge(new EdgeDraft(name, ip, RelationshipType.RESOLVES_TO, "resuelve a"));
            }

            n = 0;
            for (JsonNode cve : vulns) {
                if (n++ >= MAX_CVES) break;
                String idCve = cve.asText("").toUpperCase();
                if (!com.orion.core.util.NetUtil.isCve(idCve)) continue;
                OrionEntity ce = OrionEntity.of(EntityType.CVE, idCve).attr("source", id());
                ce.addSource(id());
                ce.setRiskScore(60);
                r.entity(ce);
                r.edge(new EdgeDraft(ip, idCve, RelationshipType.RELATED_TO, "expone vulnerabilidad"));
            }

            n = 0;
            for (JsonNode cpe : cpes) {
                if (n++ >= MAX_CPES) break;
                String product = readableCpe(cpe.asText(""));
                if (product.isEmpty()) continue;
                OrionEntity se = OrionEntity.of(EntityType.SOFTWARE, product)
                        .attr("cpe", cpe.asText(""))
                        .attr("source", id());
                se.addSource(id());
                r.entity(se);
                r.edge(new EdgeDraft(ip, product, RelationshipType.RELATED_TO, "ejecuta servicio"));
            }

            r.raw("ports", ports.size()).raw("vulns", vulns.size());
            return r;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                return SourceResult.ok(id(), "Sin exposición pública registrada para " + ip);
            }
            return SourceResult.error(id(), "InternetDB no disponible: " + e.getMessage());
        }
    }

    private static String join(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : array) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(n.asText(""));
        }
        return sb.length() > 300 ? sb.substring(0, 300) + "..." : sb.toString();
    }

    /** "cpe:/a:apache:http_server:2.4.7" -> "apache http_server 2.4.7". */
    static String readableCpe(String cpe) {
        if (cpe == null || cpe.isBlank()) return "";
        String s = cpe.replaceFirst("^cpe:/[aho]:", "").replace(':', ' ').trim();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
