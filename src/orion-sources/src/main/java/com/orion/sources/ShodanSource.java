package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Shodan (clave requerida): motor de busqueda de dispositivos conectados.
 * Devuelve puertos abiertos, servicios, banners y atributos de red del host.
 */
public class ShodanSource implements SourceConnector {

    public static final String ID = "shodan";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Shodan (comercial)"; }

    @Override
    public String description() { return "Superficie expuesta de un equipo: puertos abiertos, servicios y banners (clave requerida)."; }

    @Override
    public Category category() { return Category.COMMERCIAL; }

    @Override
    public boolean requiresApiKey() { return true; }

    @Override
    public String apiKeyName() { return "SHODAN_KEY"; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.SHODAN_HOST, Operation.IP_REPUTATION); }

    @Override
    public SourceResult query(SourceQuery q) {
        String key = SourceRegistry.getInstance().apiKeyManager().getKey(ID);
        if (key == null || key.isBlank()) {
            return SourceResult.error(id(), "Clave de Shodan no configurada (Fuentes > Gestión de claves API)");
        }
        String ip = q.value().trim();
        if (!com.orion.core.util.NetUtil.isIpv4(ip)) {
            return SourceResult.error(id(), "Se requiere una IPv4: " + q.value());
        }
        try {
            String body = Http.get("https://api.shodan.io/shodan/host/" + ip + "?key=" + key);
            JsonNode root = JsonUtil.parse(body);
            SourceResult r = SourceResult.ok(id(), "Superficie expuesta de " + ip);
            OrionEntity e = OrionEntity.of(EntityType.IPV4, ip);
            e.addSource(id());
            e.attr("shodanPorts", root.path("ports").size())
             .attr("org", JsonUtil.text(root, "org"))
             .attr("isp", JsonUtil.text(root, "isp"))
             .attr("os", JsonUtil.text(root, "os"))
             .attr("lastUpdate", JsonUtil.text(root, "last_update"));
            boolean hasMaliciousServices = false;
            // Puertos abiertos como nodos PORT desnudos, igual que InternetDB,
            // para que ambas fuentes fusionen el mismo nodo.
            Set<Integer> seenPorts = new HashSet<>();
            for (JsonNode p : root.path("ports")) {
                int port = p.asInt(0);
                if (port <= 0 || !seenPorts.add(port)) continue;
                String val = String.valueOf(port);
                OrionEntity pe = OrionEntity.of(EntityType.PORT, val).attr("protocol", "tcp");
                pe.addSource(id());
                r.entity(pe);
                r.edge(new EdgeDraft(ip, val, RelationshipType.HOSTS, "expone puerto"));
            }
            JsonNode data = root.path("data");
            int n = 0;
            if (data.isArray()) {
                for (JsonNode svc : data) {
                    if (n++ >= 10) break;
                    int port = svc.path("port").asInt(0);
                    String proto = JsonUtil.text(svc, "transport");
                    String product = JsonUtil.text(svc, "product");
                    if (port == 0) continue;
                    String val = String.valueOf(port);
                    OrionEntity pe = OrionEntity.of(EntityType.PORT, val)
                            .attr("protocol", proto.isEmpty() ? "tcp" : proto)
                            .attr("product", product)
                            .attr("banner", JsonUtil.text(svc, "data").length() > 120
                                    ? JsonUtil.text(svc, "data").substring(0, 120) + "..." : JsonUtil.text(svc, "data"));
                    pe.addSource(id());
                    r.entity(pe);
                    r.edge(new EdgeDraft(ip, val, RelationshipType.HOSTS, "expone puerto"));
                    if (product.toLowerCase().matches(".*(cobalt|metasploit|meterpreter).*")) hasMaliciousServices = true;
                }
            }
            if (hasMaliciousServices) e.setRiskScore(85);
            else e.setRiskScore(Math.min(50, root.path("ports").size() * 4));
            r.entity(e);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Shodan fallo: " + e.getMessage());
        }
    }
}
