package com.orion.app;

import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.Priority;
import com.orion.core.model.RelationshipType;
import com.orion.core.model.InvestigationStatus;
import com.orion.graph.OrionGraph;
import com.orion.intel.IntelService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de demostración: campaña de ransomware LockBit contra el sector
 * salud con infraestructura C2, certificados CT, muestras, actor y TTPs.
 * Refleja el escenario de referencia de las capturas de especificacion.
 */
public class DemoDataService {

    private final IntelService intel;

    public DemoDataService(IntelService intel) {
        this.intel = intel;
    }

    public void load() {
        Investigation inv = new Investigation("Campaña LockBit - sector salud (ES)");
        inv.setDescription("Investigación de intrusión con doble extorsión: acceso inicial vía VPN con CVE-2023-4966, "
                + "despliegue de LockBit 3.0 y exfiltración a infraestructura M247. Caso de demostración de Estoa.");
        inv.setStatus(InvestigationStatus.IN_PROGRESS);
        inv.setPriority(Priority.CRITICAL);
        inv.setAnalyst("demo");
        inv.setClassification("TLP:AMBER");
        inv.addTag("ransomware");
        inv.addTag("lockbit");
        inv.addTag("sector-salud");
        inv.addTag("doble-extorsión");
        inv.addNote("C2 confirmado por dos fuentes independientes (Feodo + ThreatFox).");
        inv.addNote("El certificado CT cubre los tres subdominios: posible operador común.");
        inv.addNote("Pendiente: validar el artefacto del 45.155.148.10 con el equipo de forense.");

        OrionGraph g = new OrionGraph();

        // cluster infraestructura
        OrionEntity d1 = ent(g, EntityType.DOMAIN, "cdn-update-example.ru", 74, "crtsh", "rdap")
                .attr("registrar", "Regional Network Information Center")
                .attr("rdap.registration", "2024-08-12")
                .attr("status", "ok")
                .attr("nameservers", "ns1.m247-cloud.eu ns2.m247-cloud.eu");
        OrionEntity d2 = ent(g, EntityType.DOMAIN, "mail-cdn-update-example.ru", 58, "crtsh")
                .attr("parentDomain", "cdn-update-example.ru");
        OrionEntity d3 = ent(g, EntityType.DOMAIN, "vpn-cdn-update-example.ru", 66, "crtsh")
                .attr("parentDomain", "cdn-update-example.ru");
        OrionEntity d4 = ent(g, EntityType.DOMAIN, "cdn-update-examp1e.ru", 69, "crtsh")
                .attr("parentDomain", "-")
                .attr("note", "variante typosquatting observada en el mismo ASN");
        OrionEntity cert = ent(g, EntityType.CERTIFICATE, "CT-8812399", 52, "crtsh")
                .attr("issuer", "CN=R3, O=Let's Encrypt")
                .attr("notBefore", "2024-08-14")
                .attr("coversDomain", "cdn-update-example.ru");
        OrionEntity ip1 = ent(g, EntityType.IPV4, "185.220.101.5", 88, "feodo", "threatfox", "ip-api")
                .attr("country", "Países Bajos").attr("city", "Amsterdam")
                .attr("asn", "AS9009").attr("feodoC2", true).attr("isHosting", true);
        OrionEntity ip2 = ent(g, EntityType.IPV4, "185.220.101.34", 72, "threatfox", "ip-api")
                .attr("country", "Países Bajos").attr("asn", "AS9009");
        OrionEntity ip3 = ent(g, EntityType.IPV4, "185.220.101.77", 64, "feodo", "ip-api")
                .attr("country", "Países Bajos").attr("asn", "AS9009");
        OrionEntity ip4 = ent(g, EntityType.IPV4, "45.155.148.10", 81, "threatfox")
                .attr("country", "Rumanía").attr("asn", "AS200000");
        OrionEntity asn = ent(g, EntityType.ASN, "AS9009", 55, "team-cymru")
                .attr("holder", "M247 Europe SRL").attr("country", "RO");
        OrionEntity asn2 = ent(g, EntityType.ASN, "AS200000", 48, "team-cymru")
                .attr("holder", "Nokia? - alojamiento RO").attr("country", "RO");
        OrionEntity netblock = ent(g, EntityType.NETBLOCK, "185.220.101.0/24", 60, "team-cymru")
                .attr("asn", "AS9009");
        OrionEntity registrar = ent(g, EntityType.REGISTRAR, "Regional Network Information Center", 35, "rdap");
        OrionEntity url = ent(g, EntityType.URL, "hxxp://cdn-update-example.ru/locker.bin", 78, "urlhaus")
                .attr("threat", "LockBit").attr("status", "en línea");

        edge(g, d1, ip1, RelationshipType.RESOLVES_TO);
        edge(g, d1, ip2, RelationshipType.RESOLVES_TO);
        edge(g, d2, ip2, RelationshipType.RESOLVES_TO);
        edge(g, d3, ip3, RelationshipType.RESOLVES_TO);
        edge(g, d4, ip3, RelationshipType.RESOLVES_TO);
        edge(g, d1, cert, RelationshipType.HAS_CERTIFICATE);
        edge(g, d2, cert, RelationshipType.HAS_CERTIFICATE);
        edge(g, d3, cert, RelationshipType.HAS_CERTIFICATE);
        edge(g, ip1, asn, RelationshipType.BELONGS_TO);
        edge(g, ip2, asn, RelationshipType.BELONGS_TO);
        edge(g, ip3, asn, RelationshipType.BELONGS_TO);
        edge(g, ip4, asn2, RelationshipType.BELONGS_TO);
        edge(g, asn, netblock, RelationshipType.OPERATES);
        edge(g, d1, registrar, RelationshipType.REGISTRAR_OF);
        edge(g, url, d1, RelationshipType.HOSTS);

        // cluster malware
        OrionEntity lockbit = ent(g, EntityType.MALWARE, "LockBit 3.0", 84, "malwarebazaar", "threatfox")
                .attr("family", "LockBit").attr("firstSeen", "2024-09-02");
        OrionEntity h1 = ent(g, EntityType.HASH, "44d88612fea8a8f36de82e1278abb02f", 70, "malwarebazaar")
                .attr("family", "LockBit 3.0").attr("fileType", "EXE x86-64").attr("firstSeen", "2024-09-03");
        OrionEntity h2 = ent(g, EntityType.HASH, "9f2b7c1d3a4e5f60718293a4b5c6d7e8", 68, "malwarebazaar")
                .attr("family", "LockBit 3.0").attr("fileType", "DLL x86-64").attr("firstSeen", "2024-09-05");
        edge(g, lockbit, h1, RelationshipType.DROPS);
        edge(g, lockbit, h2, RelationshipType.DROPS);
        edge(g, lockbit, h1, RelationshipType.VARIANT_OF);
        edge(g, url, lockbit, RelationshipType.DOWNLOADS);

        // clúster actor + campaña
        OrionEntity actor = ent(g, EntityType.APT, "LockBit", 82, "mitre-attack")
                .attr("aliases", "Bitwise Spider - ABCD ransomware")
                .attr("origin", "Rusia (RaaS)")
                .attr("description", "Ransomware como servicio (RaaS) de mayor actividad histórica;"
                        + " afiliado con exfiltración y doble extorsión.")
                .attr("attckId", "");
        OrionEntity campaign = ent(g, EntityType.CAMPAIGN, "LockBit ES-Health 2024-Q3", 76, "analista")
                .attr("window", "2024-09 a 2024-10");
        OrionEntity org = ent(g, EntityType.ORGANIZATION, "Hospital Clínico Central", 30, "ransomwarelive")
                .attr("ransomGroup", "LockBit")
                .attr("publishedAt", "2024-09-18")
                .attr("country", "ES");
        edge(g, actor, campaign, RelationshipType.OPERATES);
        edge(g, campaign, org, RelationshipType.TARGETS);
        edge(g, lockbit, actor, RelationshipType.RELATED_TO);
        edge(g, actor, url, RelationshipType.USES_TECHNIQUE);

        // TTPs
        Map<String, String> techs = new LinkedHashMap<>();
        techs.put("T1133", "Servicios remotos externos");
        techs.put("T1190", "Explotación de aplicación expuesta");
        techs.put("T1486", "Datos cifrados para impacto");
        techs.put("T1490", "Inhibición de la recuperación del sistema");
        techs.put("T1562", "Degradación de defensas");
        techs.put("T1078", "Cuentas válidas");
        for (var entry : techs.entrySet()) {
            OrionEntity t = ent(g, EntityType.ATTCK_TECHNIQUE, entry.getKey(), 50, "mitre-attack")
                    .attr("name", entry.getValue())
                    .attr("tactic", tacticOf(entry.getKey()));
            edge(g, actor, t, RelationshipType.USES_TECHNIQUE);
        }
        OrionEntity ta1 = ent(g, EntityType.ATTCK_TACTIC, "TA0001", 40, "mitre-attack")
                .attr("name_es", "Acceso inicial");
        OrionEntity ta2 = ent(g, EntityType.ATTCK_TACTIC, "TA0040", 40, "mitre-attack")
                .attr("name_es", "Impacto");
        edge(g, entRef(g, "T1133"), ta1, RelationshipType.MEMBER_OF);
        edge(g, entRef(g, "T1190"), ta1, RelationshipType.MEMBER_OF);
        edge(g, entRef(g, "T1486"), ta2, RelationshipType.MEMBER_OF);
        edge(g, entRef(g, "T1490"), ta2, RelationshipType.MEMBER_OF);

        // CVE explotada
        OrionEntity cve = ent(g, EntityType.CVE, "CVE-2023-4966", 93, "nvd", "cisa-kev")
                .attr("cvss", 9.8).attr("severity", "CRÍTICA")
                .attr("kev", true).attr("kevAdded", "2023-10-18")
                .attr("description", "Fuga de información sensible en NetScaler ADC y Gateway (Citrix Bleed).");
        edge(g, actor, cve, RelationshipType.EXPLOITS);
        edge(g, cve, d3, RelationshipType.RELATED_TO);

        // persona contacto del grupo
        OrionEntity contact = ent(g, EntityType.EMAIL, "lockbit-support@onionmail.org", 44, "analista");
        edge(g, actor, contact, RelationshipType.OPERATES);

        intel.setCurrentCase(inv, g);
    }

    private OrionEntity ent(OrionGraph g, EntityType type, String value, double risk, String... sources) {
        OrionEntity e = OrionEntity.of(type, value);
        e.setRiskScore(risk);
        e.setSource(sources.length > 0 ? sources[0] : "manual");
        for (String s : sources) e.addSource(s);
        g.addEntity(e);
        return e;
    }

    private OrionEntity entRef(OrionGraph g, String value) {
        return g.nodeByValue(value).map(com.orion.graph.GraphNode::getEntity).orElseThrow();
    }

    private void edge(OrionGraph g, OrionEntity from, OrionEntity to, RelationshipType rel) {
        g.addEdge(from, to, rel, rel.label());
    }

    private String tacticOf(String tech) {
        return switch (tech) {
            case "T1133", "T1190", "T1078" -> "TA0001";
            case "T1486", "T1490" -> "TA0040";
            case "T1562" -> "TA0005";
            default -> "TA0002";
        };
    }
}
