package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * ip-api.com: geolocalizacion y atribucion de ASN para direcciones IP.
 * Nivel gratuito sin clave (45 consultas/minuto, solo HTTP).
 */
public class IpApiSource implements SourceConnector {

    public static final String ID = "ip-api";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "ip-api (geolocalización y ASN)"; }

    @Override
    public String description() { return "Geolocalización, ISP y ASN de una IP (nivel gratuito, sin clave)."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.IP_GEO, Operation.IP_ASN); }

    @Override
    public SourceResult query(SourceQuery q) {
        String ip = q.value().trim();
        if (!com.orion.core.util.NetUtil.isIpv4(ip)) {
            return SourceResult.error(id(), "Se requiere una IPv4 válida: " + q.value());
        }
        try {
            String body = Http.get("http://ip-api.com/json/" + ip + "?fields=status,country,countryCode,regionName,city,isp,org,as,asNumber,reverse,mobile,proxy,hosting,query");
            JsonNode root = JsonUtil.parse(body);
            if (!"success".equals(JsonUtil.text(root, "status"))) {
                return SourceResult.error(id(), "ip-api fallo para " + ip);
            }
            SourceResult r = SourceResult.ok(id(), "Datos de red para " + ip);
            OrionEntity e = OrionEntity.of(EntityType.IPV4, ip);
            e.addSource(id());
            e.attr("country", JsonUtil.text(root, "country"))
             .attr("countryCode", JsonUtil.text(root, "countryCode"))
             .attr("region", JsonUtil.text(root, "regionName"))
             .attr("city", JsonUtil.text(root, "city"))
             .attr("isp", JsonUtil.text(root, "isp"))
             .attr("org", JsonUtil.text(root, "org"))
             .attr("reverse", JsonUtil.text(root, "reverse"))
             .attr("isProxy", root.path("proxy").asBoolean(false))
             .attr("isHosting", root.path("alojamiento").asBoolean(false));
            r.entity(e);

            String asText = JsonUtil.text(root, "as");
            long asNumber = root.path("asNumber").asLong(0);
            if (asNumber > 0) {
                String asnValue = "AS" + asNumber;
                String asnLabel = asText.isEmpty() ? asnValue : asnValue + " (" + asText.split(",")[0].trim() + ")";
                OrionEntity asn = OrionEntity.of(EntityType.ASN, asnValue)
                        .attr("name", asnLabel).attr("holder", JsonUtil.text(root, "org"));
                asn.addSource(id());
                r.entity(asn);
                r.edge(EdgeDraft.of(ip, asnValue, RelationshipType.BELONGS_TO));
            }
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "ip-api no disponible: " + e.getMessage());
        }
    }
}
