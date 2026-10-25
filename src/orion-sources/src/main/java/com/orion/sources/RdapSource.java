package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;
import com.orion.core.util.NetUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * RDAP (Registration Data Access Protocol): sucesor de WHOIS sin limite de
 * consultas, con el redirector rdap.org como puerta de entrada a los
 * registradores y registros regionales.
 *
 * <p>Dominios: registrador patrocinante, identificador IANA, fechas, estados,
 * servidores NS y contacto de abuso oficial.
 *
 * <p>IPs: bloque de red (start/end), nombre de red, pais, tipo de asignacion y
 * contacto de abuso del RIR o del proveedor de alojamiento.
 */
public class RdapSource implements SourceConnector {

    public static final String ID = "rdap";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "RDAP (registro de dominios e IPs)"; }

    @Override
    public String description() { return "Registro: registrador, fechas, NS y estados de un dominio; bloque, paÃ­s y abuso de una IP vÃ­a rdap.org."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.WHOIS); }

    @Override
    public SourceResult query(SourceQuery q) {
        String value = q.value().trim();
        if (NetUtil.isIpv4(value) || NetUtil.isIpv6(value)) {
            return queryIp(value);
        }
        return queryDomain(NetUtil.normalizeDomain(value));
    }

    // ---- dominio ----

    private SourceResult queryDomain(String domain) {
        if (!NetUtil.isDomain(domain)) {
            return SourceResult.error(id(), "Dominio invÃ¡lido: " + domain);
        }
        try {
            JsonNode root = JsonUtil.parse(Http.get("https://rdap.org/domain/" + domain));
            if (root.path("errorCode").asInt(0) > 0) {
                return SourceResult.ok(id(), "Sin registro RDAP para " + domain);
            }

            List<Contact> registrars = contacts(root, "registrar");
            String registrar = registrars.isEmpty() ? "" : registrars.get(0).name();
            if (registrar.isEmpty()) registrar = JsonUtil.text(root, "handle");

            List<Contact> abuse = contacts(root, "abuse");
            String abuseEmail = firstEmail(abuse);

            SourceResult r = SourceResult.ok(id(), "Registro RDAP de " + domain);
            OrionEntity dom = OrionEntity.of(EntityType.DOMAIN, domain);
            dom.addSource(id());

            if (!registrar.isEmpty()) {
                dom.attr("registrar", registrar);
                String ianaId = registrarIanaId(root);
                if (!ianaId.isEmpty()) dom.attr("registrarIanaId", ianaId);
                OrionEntity reg = OrionEntity.of(EntityType.REGISTRAR, registrar)
                        .attr("forDomain", domain)
                        .attr("source", id());
                if (!ianaId.isEmpty()) reg.attr("ianaId", ianaId);
                reg.addSource(id());
                r.entity(reg);
                r.edge(new EdgeDraft(domain, registrar, RelationshipType.REGISTRAR_OF, "registrador de"));
            }

            for (JsonNode ev : root.path("events")) {
                String action = JsonUtil.text(ev, "eventAction");
                String date = JsonUtil.text(ev, "eventDate");
                if (!action.isEmpty() && !date.isEmpty()) dom.attr("rdap." + action, date);
            }
            if (root.path("status").isArray()) {
                List<String> states = new ArrayList<>();
                root.path("status").forEach(s -> states.add(s.asText("")));
                dom.attr("status", String.join(", ", states));
            }

            JsonNode ns = root.path("nameservers");
            if (ns.isArray() && !ns.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (JsonNode n : ns) {
                    String name = JsonUtil.text(n, "ldhName");
                    if (name.isEmpty()) name = JsonUtil.text(n, "unicodeName");
                    if (!name.isEmpty()) names.add(name.toLowerCase());
                }
                dom.attr("nameservers", String.join(", ", names));
            }

            if (!abuseEmail.isEmpty()) {
                dom.attr("abuseContact", abuseEmail);
                OrionEntity email = OrionEntity.of(EntityType.EMAIL, abuseEmail)
                        .attr("role", "abuso")
                        .attr("forDomain", domain)
                        .attr("source", id());
                email.addSource(id());
                r.entity(email);
                r.edge(new EdgeDraft(domain, abuseEmail, RelationshipType.RELATED_TO, "contacto de abuso"));
            }

            r.entity(dom);
            r.raw("registrar", registrar).raw("abuse", abuseEmail);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "RDAP no disponible para " + domain + ": " + e.getMessage());
        }
    }

    // ---- IP ----

    private SourceResult queryIp(String ip) {
        try {
            JsonNode root = JsonUtil.parse(Http.get("https://rdap.org/ip/" + ip));
            if (root.path("errorCode").asInt(0) > 0) {
                return SourceResult.ok(id(), "Sin registro RDAP para " + ip);
            }

            String start = JsonUtil.text(root, "startAddress");
            String end = JsonUtil.text(root, "endAddress");
            String netname = JsonUtil.text(root, "name");
            if (netname.isEmpty()) netname = JsonUtil.text(root, "handle");

            EntityType type = NetUtil.isIpv4(ip) ? EntityType.IPV4 : EntityType.IPV6;
            OrionEntity host = OrionEntity.of(type, ip);
            host.addSource(id());
            if (!netname.isEmpty()) host.attr("netName", netname);
            String country = JsonUtil.text(root, "country");
            if (!country.isEmpty()) host.attr("country", country);
            String allocation = JsonUtil.text(root, "type");
            if (!allocation.isEmpty()) host.attr("allocationType", allocation);

            SourceResult r = SourceResult.ok(id(), "Registro RDAP de " + ip
                    + (netname.isEmpty() ? "" : " (" + netname + ")"));
            r.entity(host);

            String block = "";
            if (!start.isEmpty() && !end.isEmpty()) {
                block = start.equals(end) ? start : start + "-" + end;
                OrionEntity netblock = OrionEntity.of(EntityType.NETBLOCK, block)
                        .attr("startAddress", start)
                        .attr("endAddress", end)
                        .attr("netName", netname)
                        .attr("country", country)
                        .attr("source", id());
                netblock.addSource(id());
                r.entity(netblock);
                r.edge(new EdgeDraft(ip, block, RelationshipType.BELONGS_TO, "pertenece al bloque"));
            }

            List<Contact> abuse = contacts(root, "abuse");
            String abuseEmail = firstEmail(abuse);
            String orgName = "";
            if (!abuse.isEmpty()) orgName = abuse.get(0).org();
            if (orgName.isEmpty()) {
                List<Contact> admins = contacts(root, "administrative");
                if (admins.isEmpty()) admins = contacts(root, "technical");
                if (!admins.isEmpty()) orgName = admins.get(0).org();
            }
            if (orgName.isEmpty()) orgName = JsonUtil.text(root, "name");
            // el nombre de red no suele identificar al titular; se descarta
            if (orgName.equalsIgnoreCase(netname)) orgName = "";

            if (!orgName.isEmpty()) {
                host.attr("organization", orgName);
                OrionEntity org = OrionEntity.of(EntityType.ORGANIZATION, orgName)
                        .attr("operatesBlock", block)
                        .attr("source", id());
                org.addSource(id());
                r.entity(org);
                if (!block.isEmpty()) {
                    r.edge(new EdgeDraft(orgName, block, RelationshipType.OPERATES, "opera el bloque"));
                } else {
                    r.edge(new EdgeDraft(orgName, ip, RelationshipType.BELONGS_TO, "titular de la IP"));
                }
            }

            if (!abuseEmail.isEmpty()) {
                host.attr("abuseContact", abuseEmail);
                OrionEntity email = OrionEntity.of(EntityType.EMAIL, abuseEmail)
                        .attr("role", "abuso")
                        .attr("forIp", ip)
                        .attr("source", id());
                email.addSource(id());
                r.entity(email);
                r.edge(new EdgeDraft(ip, abuseEmail, RelationshipType.RELATED_TO, "contacto de abuso"));
            }

            r.raw("netblock", block).raw("abuse", abuseEmail).raw("organization", orgName);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "RDAP no disponible para " + ip + ": " + e.getMessage());
        }
    }

    // ---- vCard y contactos ----

    /** Contacto extraido de un objeto vCard de RDAP. */
    private record Contact(String name, String email, String org) {}

    /**
     * Recorre el arbol de entidades (incluidas las anidadas) y recoge los
     * contactos con el rol indicado. El vCard de RDAP no tiene un orden fijo:
     * se leen los campos por nombre, no por posicion.
     */
    private static List<Contact> contacts(JsonNode root, String role) {
        List<Contact> out = new ArrayList<>();
        collectContacts(root.path("entities"), role, out, 0);
        return out;
    }

    private static void collectContacts(JsonNode entities, String role, List<Contact> out, int depth) {
        if (!entities.isArray() || depth > 4) return;
        for (JsonNode e : entities) {
            if (hasRole(e, role)) {
                String name = vcard(e, "fn");
                if (name.isEmpty()) name = vcard(e, "org");
                if (name.isEmpty()) name = JsonUtil.text(e, "handle");
                out.add(new Contact(name, vcard(e, "email"), vcard(e, "org")));
            }
            collectContacts(e.path("entities"), role, out, depth + 1);
        }
    }

    private static boolean hasRole(JsonNode entity, String role) {
        for (JsonNode r : entity.path("roles")) {
            if (role.equalsIgnoreCase(r.asText(""))) return true;
        }
        return false;
    }

    /** Primer valor del campo indicado dentro de {@code vcardArray[1]}. */
    private static String vcard(JsonNode entity, String field) {
        JsonNode entries = entity.path("vcardArray").path(1);
        if (!entries.isArray()) return "";
        for (JsonNode entry : entries) {
            if (!field.equalsIgnoreCase(entry.path(0).asText(""))) continue;
            JsonNode value = entry.path(3);
            if (value.isArray()) {
                return value.isEmpty() ? "" : value.path(0).asText("").trim();
            }
            String text = value.asText("").trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String registrarIanaId(JsonNode root) {
        for (JsonNode e : root.path("entities")) {
            if (!hasRole(e, "registrar")) continue;
            for (JsonNode id : e.path("publicIds")) {
                if ("IANA Registrar ID".equalsIgnoreCase(JsonUtil.text(id, "type"))) {
                    return JsonUtil.text(id, "identifier");
                }
            }
        }
        return "";
    }

    private static String firstEmail(List<Contact> contacts) {
        for (Contact c : contacts) {
            if (!c.email().isEmpty()) return c.email().toLowerCase();
        }
        return "";
    }
}
