package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Team Cymru IP-to-ASN: servicio whois en el puerto 43 que devuelve la
 * atribucion autonoma oficial del registro. Sin clave y con alta fiabilidad.
 */
public class TeamCymruSource implements SourceConnector {

    public static final String ID = "team-cymru";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Team Cymru IP-to-ASN"; }

    @Override
    public String description() { return "Atribución ASN oficial vía whois (puerto 43) de Team Cymru, con bloque de red y país."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.IP_ASN); }

    @Override
    public SourceResult query(SourceQuery q) {
        String ip = q.value().trim();
        if (!com.orion.core.util.NetUtil.isIpv4(ip) && !com.orion.core.util.NetUtil.isIpv6(ip)) {
            return SourceResult.error(id(), "IP inválida: " + q.value());
        }
        try {
            List<String> lines = whois(ip);
            if (lines.isEmpty()) return SourceResult.ok(id(), "Sin datos ASN para " + ip);
            String asn = "", netblock = "", country = "", holder = "";
            for (String line : lines) {
                String[] kv = line.split("\\|");
                if (kv.length >= 7) {
                    asn = kv[0].trim();
                    netblock = kv[2].trim();
                    country = kv[3].trim();
                    holder = kv[6].trim();
                }
            }
            if (asn.isEmpty() || "NA".equals(asn)) return SourceResult.ok(id(), "IP sin atribución ASN");
            SourceResult r = SourceResult.ok(id(), "Atribución ASN para " + ip);
            OrionEntity e = OrionEntity.of(EntityType.IPV4, ip);
            e.addSource(id());
            e.attr("asn", asn).attr("netblock", netblock).attr("country", country);
            r.entity(e);

            String asnValue = asn.startsWith("AS") ? asn : "AS" + asn.replace(".0", "");
            OrionEntity as = OrionEntity.of(EntityType.ASN, asnValue)
                    .attr("holder", holder).attr("country", country);
            as.addSource(id());
            r.entity(as);
            r.edge(EdgeDraft.of(ip, asnValue, RelationshipType.BELONGS_TO));

            if (!netblock.isEmpty() && !"NA".equals(netblock)) {
                OrionEntity nb = OrionEntity.of(EntityType.NETBLOCK, netblock).attr("asn", asnValue);
                nb.addSource(id());
                r.entity(nb);
                r.edge(EdgeDraft.of(asnValue, netblock, RelationshipType.OPERATES));
            }
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Team Cymru inaccesible: " + e.getMessage());
        }
    }

    private List<String> whois(String ip) throws Exception {
        try (Socket socket = new Socket("whois.cymru.com", 43)) {
            socket.setSoTimeout((int) Duration.ofSeconds(15).toMillis());
            socket.getOutputStream().write((" -v -r " + ip + "\r\n").getBytes(StandardCharsets.UTF_8));
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                return in.lines().filter(l -> l.contains("|")).toList();
            }
        }
    }
}
