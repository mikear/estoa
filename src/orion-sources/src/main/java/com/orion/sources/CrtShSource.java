package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * crt.sh: certificacion transparencia (CT) de Certificate Transparency logs.
 * Descubre subdominios y certificados emitidos para un dominio, incluyendo
 * dominios comodin y certificados de actores maliciosos.
 */
public class CrtShSource implements SourceConnector {

    public static final String ID = "crtsh";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "crt.sh (Certificate Transparency)"; }

    @Override
    public String description() { return "Descubrimiento de subdominios y certificados a partir de los logs CT públicos."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.SUBDOMAINS, Operation.CERTIFICATES); }

    @Override
    public SourceResult query(SourceQuery q) {
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        if (!com.orion.core.util.NetUtil.isDomain(domain)) {
            return SourceResult.error(id(), "Dominio inválido: " + q.value());
        }
        boolean wantSubdomains = q.operation() == Operation.SUBDOMAINS;
        boolean wantCertificates = q.operation() == Operation.CERTIFICATES;
        if (!wantSubdomains && !wantCertificates) {
            return SourceResult.error(id(), "Operación no soportada: " + q.operation());
        }
        try {
            String url = "https://crt.sh/?q=%25." + domain + "&output=json&limit=200";
            String body = Http.get(url);
            JsonNode root = JsonUtil.parse(body);
            if (!root.isArray() || root.isEmpty()) {
                return SourceResult.ok(id(), wantSubdomains
                        ? "Sin subdominios CT para " + domain
                        : "Sin certificados CT para " + domain);
            }
            java.util.List<OrionEntity> found = new java.util.ArrayList<>();
            java.util.List<EdgeDraft> foundEdges = new java.util.ArrayList<>();
            Set<String> subs = new LinkedHashSet<>();
            Set<String> certs = new LinkedHashSet<>();
            for (JsonNode cert : root) {
                String issuer = JsonUtil.text(cert, "issuer_name");
                String entry = JsonUtil.text(cert, "entry_timestamp");
                String notBefore = JsonUtil.text(cert, "not_before");
                long idc = cert.path("id").asLong();
                for (String name : JsonUtil.text(cert, "name_value").split("\\n")) {
                    String sub = name.trim().toLowerCase().replace("*.", "");
                    if (wantSubdomains && sub.endsWith("." + domain) && sub.indexOf('.') > 0) {
                        if (!subs.add(sub)) continue;
                        OrionEntity s = OrionEntity.of(EntityType.DOMAIN, sub)
                                .attr("parentDomain", domain)
                                .attr("firstSeenCT", notBefore);
                        s.addSource(id());
                        found.add(s);
                        foundEdges.add(EdgeDraft.of(sub, domain, RelationshipType.RELATED_TO));
                    } else if (wantCertificates && sub.equals(domain)) {
                        String certKey = idc > 0 ? "CT-" + idc : "CT-" + JsonUtil.text(cert, "serial_number");
                        if (certKey.equals("CT-") || !certs.add(certKey)) continue;
                        OrionEntity c = OrionEntity.of(EntityType.CERTIFICATE, certKey)
                                .attr("issuer", issuer.length() > 80 ? issuer.substring(0, 80) + "..." : issuer)
                                .attr("notBefore", notBefore)
                                .attr("entryTimestamp", entry)
                                .attr("coversDomain", domain);
                        c.addSource(id());
                        found.add(c);
                        foundEdges.add(EdgeDraft.of(domain, certKey, RelationshipType.HAS_CERTIFICATE));
                    }
                }
            }
            String message = wantSubdomains
                    ? subs.size() + " subdominios desde " + root.size() + " registros CT"
                    : certs.size() + " certificados desde " + root.size() + " registros CT";
            if ((wantSubdomains && subs.isEmpty()) || (wantCertificates && certs.isEmpty())) {
                message = wantSubdomains
                        ? "Sin subdominios CT para " + domain
                        : "Sin certificados CT para " + domain;
            }
            SourceResult r = SourceResult.ok(id(), message);
            found.forEach(r::entity);
            foundEdges.forEach(r::edge);
            r.raw("subdomains", subs.size()).raw("certificates", certs.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "crt.sh no disponible: " + e.getMessage());
        }
    }
}
