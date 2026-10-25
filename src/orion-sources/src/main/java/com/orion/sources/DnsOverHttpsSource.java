package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Resolucion DNS sobre HTTPS (DoH) usando los resolutores publicos de Google
 * y Cloudflare. Evita la fuga de consultas hacia resolutores locales y
 * permite trabajar desde redes restringidas.
 */
public class DnsOverHttpsSource implements SourceConnector {

    public static final String ID = "dns-doh";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "DNS sobre HTTPS (Google/Cloudflare)"; }

    @Override
    public String description() { return "Resolución A, AAAA, MX, NS y TXT vía JSON DoH sin clave de API."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() {
        return Set.of(Operation.DNS_A, Operation.DNS_AAAA, Operation.DNS_MX, Operation.DNS_NS, Operation.DNS_TXT);
    }

    @Override
    public SourceResult query(SourceQuery q) {
        String type = switch (q.operation()) {
            case DNS_A -> "A";
            case DNS_AAAA -> "AAAA";
            case DNS_MX -> "MX";
            case DNS_NS -> "NS";
            case DNS_TXT -> "TXT";
            default -> null;
        };
        if (type == null) return SourceResult.error(id(), "Operación no soportada: " + q.operation());
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        if (!NetUtil_valid(domain)) return SourceResult.error(id(), "Dominio inválido: " + q.value());

        try {
            String url = "https://dns.google/resolve?name=" + encode(domain) + "&type=" + type;
            String body;
            try {
                body = Http.get(url);
            } catch (Exception retryCloudflare) {
                body = Http.get("https://cloudflare-dns.com/dns-query?name=" + encode(domain) + "&type=" + type,
                        java.util.Map.of("Accept", "application/dns-json"));
            }
            JsonNode root = JsonUtil.parse(body);
            int status = root.path("Status").asInt(-1);
            if (status == 3) return SourceResult.ok(id(), "El dominio " + domain + " no existe (NXDOMAIN)");
            JsonNode answers = root.path("Answer");
            if (answers.isMissingNode() || !answers.isArray() || answers.isEmpty()) {
                return SourceResult.ok(id(), "Sin registros " + type + " para " + domain);
            }
            SourceResult r = SourceResult.ok(id(), answers.size() + " registros " + type + " para " + domain);
            for (JsonNode a : answers) {
                String data = a.path("data").asText("");
                if (data.isEmpty()) continue;
                switch (type) {
                    case "A" -> {
                        OrionEntity ip = OrionEntity.of(EntityType.IPV4, data).attr("resolvedFrom", domain);
                        ip.addSource(id());
                        r.entity(ip);
                        r.edge(EdgeDraft.of(domain, data, RelationshipType.RESOLVES_TO));
                    }
                    case "AAAA" -> {
                        OrionEntity ip = OrionEntity.of(EntityType.IPV6, data).attr("resolvedFrom", domain);
                        ip.addSource(id());
                        r.entity(ip);
                        r.edge(EdgeDraft.of(domain, data, RelationshipType.RESOLVES_TO));
                    }
                    case "MX" -> {
                        String host = data.split(" ")[data.split(" ").length - 1];
                        OrionEntity rec = OrionEntity.of(EntityType.DNS_RECORD, host)
                                .attr("type", "MX").attr("raw", data).attr("domain", domain);
                        rec.addSource(id());
                        r.entity(rec);
                        r.edge(new EdgeDraft(domain, host, RelationshipType.RELATED_TO, "registro MX"));
                    }
                    case "NS" -> {
                        OrionEntity rec = OrionEntity.of(EntityType.DNS_RECORD, data)
                                .attr("type", "NS").attr("domain", domain);
                        rec.addSource(id());
                        r.entity(rec);
                        r.edge(new EdgeDraft(domain, data, RelationshipType.RELATED_TO, "registro NS"));
                    }
                    default -> {
                        String txt = data.length() > 60 ? data.substring(0, 60) + "..." : data;
                        OrionEntity rec = OrionEntity.of(EntityType.DNS_RECORD, txt)
                                .attr("type", "TXT").attr("raw", data).attr("domain", domain);
                        rec.addSource(id());
                        r.entity(rec);
                        r.edge(new EdgeDraft(domain, txt, RelationshipType.RELATED_TO, "registro TXT"));
                    }
                }
            }
            r.raw("domain", domain).raw("type", type);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Fallo DoH para " + domain + " (" + type + "): " + e.getMessage());
        }
    }

    private static boolean NetUtil_valid(String domain) {
        return com.orion.core.util.NetUtil.isDomain(domain) || domain.equals("localhost");
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
