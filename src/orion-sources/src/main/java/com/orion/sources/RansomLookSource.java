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
 * RansomLook (ransomlook.io): agregador de publicaciones de grupos de
 * extorsion. Se consulta por dominio y se devuelven los posts que lo reclaman
 * como victima, con grupo, fecha y enlace (licencia CC BY 4.0).
 */
public class RansomLookSource implements SourceConnector {

    public static final String ID = "ransomlook";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "RansomLook (ransomlook.io)"; }

    @Override
    public String description() { return "Publicaciones de grupos de ransomware que reclaman un dominio como víctima."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.DOMAIN_THREATS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        if (!com.orion.core.util.NetUtil.isDomain(domain)) {
            return SourceResult.error(id(), "Dominio no válido: " + q.value());
        }
        try {
            String url = "https://www.ransomlook.io/api/search?q="
                    + URLEncoder.encode(domain, StandardCharsets.UTF_8);
            String body = Http.get(url);
            JsonNode root = JsonUtil.parse(body);
            JsonNode posts = root.path("posts");
            if (!posts.isArray() || posts.isEmpty()) {
                return SourceResult.ok(id(), "Sin publicaciones de extorsión para " + domain);
            }

            SourceResult r = SourceResult.ok(id(), posts.size() + " publicación(es) de extorsión para " + domain);
            int n = 0;
            for (JsonNode post : posts) {
                if (n++ >= 8) break;
                String group = JsonUtil.text(post, "group_name");
                String title = JsonUtil.text(post, "post_title");
                if (title.isEmpty()) title = domain;
                if (group.isEmpty()) group = "grupo no identificado";

                OrionEntity actor = OrionEntity.of(EntityType.THREAT_ACTOR, group)
                        .attr("source", id());
                actor.addSource(id());
                actor.setRiskScore(80);
                r.entity(actor);
                r.edge(new EdgeDraft(group, domain, RelationshipType.TARGETS, "reclama como víctima"));

                OrionEntity campaign = OrionEntity.of(EntityType.CAMPAIGN, title)
                        .attr("ransomGroup", group)
                        .attr("discovered", JsonUtil.text(post, "discovered"))
                        .attr("description", truncate(JsonUtil.text(post, "description"), 300))
                        .attr("link", JsonUtil.text(post, "link"))
                        .attr("source", id());
                campaign.addSource(id());
                campaign.setRiskScore(65);
                r.entity(campaign);
                r.edge(new EdgeDraft(group, title, RelationshipType.RELATED_TO, "publica campaña"));
            }
            r.raw("posts", posts.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "RansomLook no disponible: " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
