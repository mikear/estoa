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
 * ransomware.live: rastreador publico de victimas publicadas por grupos de
 * ransomware en sus sitios de filtracion. Permite consultar si un dominio
 * aparece como victima y que grupo lo reivindico.
 */
public class RansomwareLiveSource implements SourceConnector {

    public static final String ID = "ransomwarelive";

    private final TtlCache<List<JsonNode>> recent = new TtlCache<>(Duration.ofMinutes(30));

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "ransomware.live"; }

    @Override
    public String description() { return "Víctimas recientes publicadas por grupos de ransomware en sitios de filtración."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.DOMAIN_THREATS, Operation.RECENT_VICTIMS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String domain = com.orion.core.util.NetUtil.normalizeDomain(q.value());
        try {
            List<JsonNode> victims = recent.get(this::downloadRecent);
            if (q.operation() == Operation.RECENT_VICTIMS) {
                SourceResult r = SourceResult.ok(id(), victims.size() + " víctimas recientes en ransomware.live");
                int n = 0;
                for (JsonNode v : victims) {
                    if (n++ >= 20) break;
                    String victim = JsonUtil.text(v, "victim");
                    String group = JsonUtil.text(v, "group");
                    if (victim.isEmpty() || group.isEmpty()) continue;
                    OrionEntity org = OrionEntity.of(EntityType.ORGANIZATION, victim)
                            .attr("ransomGroup", group)
                            .attr("publishedAt", JsonUtil.text(v, "publishdate"))
                            .attr("country", JsonUtil.text(v, "country"));
                    org.setRiskScore(55);
                    org.addSource(id());
                    r.entity(org);
                    r.edge(EdgeDraft.of(victim, group, RelationshipType.TARGETS));
                    OrionEntity gang = OrionEntity.of(EntityType.THREAT_ACTOR, group);
                    gang.setRiskScore(75);
                    gang.addSource(id());
                    r.entity(gang);
                }
                return r;
            }
            // búsqueda por dominio u organización
            for (JsonNode v : victims) {
                String victim = JsonUtil.text(v, "victim").toLowerCase();
                String group = JsonUtil.text(v, "group");
                if (victim.contains(domain) || domain.contains(victim)) {
                    SourceResult r = SourceResult.ok(id(), domain + " aparece como víctima de " + group);
                    OrionEntity org = OrionEntity.of(EntityType.ORGANIZATION, JsonUtil.text(v, "victim"))
                            .attr("ransomGroup", group)
                            .attr("publishedAt", JsonUtil.text(v, "publishdate"))
                            .attr("country", JsonUtil.text(v, "country"));
                    org.addSource(id());
                    r.entity(org);
                    r.edge(EdgeDraft.of(JsonUtil.text(v, "victim"), group, RelationshipType.TARGETS));
                    OrionEntity gang = OrionEntity.of(EntityType.THREAT_ACTOR, group);
                    gang.setRiskScore(75);
                    gang.addSource(id());
                    r.entity(gang);
                    return r;
                }
            }
            SourceResult r = SourceResult.ok(id(), "Sin víctimas recientes para " + domain);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "ransomware.live no disponible: " + e.getMessage());
        }
    }

    private List<JsonNode> downloadRecent() {
        List<JsonNode> out = new ArrayList<>();
        try {
            String body = Http.get("https://api.ransomware.live/recentv2-json");
            JsonNode root = JsonUtil.parse(body);
            if (root.isArray()) root.forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }
}
