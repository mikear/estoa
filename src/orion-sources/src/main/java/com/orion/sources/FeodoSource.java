package com.orion.sources;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Feodo Tracker (AbuseCH): lista de servidores C2 de troyanos bancarios
 * (Dridex, Emotet, TrickBot, QakBot...). Se descarga la lista completa con
 * cache de 1 hora y se comprueba la membresia de la IP consultada.
 */
public class FeodoSource implements SourceConnector {

    public static final String ID = "feodo";

    private final TtlCache<Set<String>> blocklist = new TtlCache<>(Duration.ofHours(1));

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Feodo Tracker (C2)"; }

    @Override
    public String description() { return "Detección de servidores C2 de troyanos bancarios (Dridex, Emotet, QakBot...)."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.IP_REPUTATION); }

    @Override
    public SourceResult query(SourceQuery q) {
        String ip = q.value().trim();
        if (!com.orion.core.util.NetUtil.isIpv4(ip)) {
            return SourceResult.error(id(), "Se requiere IPv4: " + q.value());
        }
        try {
            Set<String> list = blocklist.get(this::download);
            SourceResult r = SourceResult.ok(id(), "IP no figura en la lista Feodo");
            if (list.contains(ip)) {
                OrionEntity e = OrionEntity.of(EntityType.IPV4, ip);
                e.addSource(id());
                e.attr("feodoC2", true).attr("checkedAt", java.time.Instant.now().toString());
                e.setRiskScore(85);
                r.entity(e);
                r.raw("c2", true);
                r = SourceResult.ok(id(), "IP identificada como C2 activo por Feodo Tracker");
                r.entity(e);
                r.raw("c2", true);
            } else {
                r.raw("c2", false);
            }
            r.raw("listSize", list.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Feodo Tracker no disponible: " + e.getMessage());
        }
    }

    private Set<String> download() {
        Set<String> ips = new HashSet<>();
        try {
            String body = Http.get("https://feodotracker.abuse.ch/downloads/ipblocklist.json");
            JsonNode root = JsonUtil.parse(body);
            if (root.isArray()) {
                for (JsonNode e : root) {
                    String ip = JsonUtil.text(e, "ip_address");
                    if (com.orion.core.util.NetUtil.isIpv4(ip)) ips.add(ip);
                }
            }
        } catch (Exception ignored) {
            // cache vacio; la proxima consulta reintentara
        }
        return ips;
    }
}
