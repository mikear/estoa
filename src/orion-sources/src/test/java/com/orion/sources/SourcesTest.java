package com.orion.sources;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SourcesTest {

    @Test
    void twentyFourConnectorsRegistered() {
        assertEquals(24, SourcesBootstrap.defaults().size());
    }

    @Test
    void registryIndexesByIdAndOperation() {
        SourceRegistry r = new SourceRegistry();
        // registro limpio para la prueba
        SourcesBootstrap.registerDefaults(r);
        assertTrue(r.byId("dns-doh").isPresent());
        assertTrue(r.byId("mitre-attack").isPresent());
        assertFalse(r.byId("no-existe").isPresent());
        assertTrue(r.forOperation(Operation.DNS_A).stream().anyMatch(c -> c.id().equals("dns-doh")));
        assertTrue(r.forOperation(Operation.IP_ASN).stream().anyMatch(c -> c.id().equals("team-cymru")));
    }

    @Test
    void commercialSourcesRequireKeys() {
        List<SourceConnector> commercial = SourcesBootstrap.defaults().stream()
                .filter(c -> c.category() == SourceConnector.Category.COMMERCIAL).toList();
        assertEquals(4, commercial.size());
        assertTrue(commercial.stream().allMatch(SourceConnector::requiresApiKey));
    }

    @Test
    void abuseChSourcesShareAuthKey() {
        for (String id : List.of("threatfox", "urlhaus", "malwarebazaar")) {
            SourceConnector c = SourcesBootstrap.defaults().stream()
                    .filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            assertTrue(c.requiresApiKey(), id + " debe exigir clave");
            assertEquals("ABUSECH_KEY", c.apiKeyName(), id);
        }
    }

    @Test
    void newSourcesAreRegisteredWithTheirOperations() {
        SourceRegistry r = new SourceRegistry();
        SourcesBootstrap.registerDefaults(r);
        for (String id : List.of("shodan-internetdb", "site-probe", "ransomlook",
                "hibp", "hudsonrock", "epss", "urlquery")) {
            assertTrue(r.byId(id).isPresent(), "falta el conector " + id);
        }
        assertTrue(r.forOperation(Operation.INTERNET_DB).stream().anyMatch(c -> c.id().equals("shodan-internetdb")));
        assertTrue(r.forOperation(Operation.SITE_PROBE).stream().anyMatch(c -> c.id().equals("site-probe")));
        assertTrue(r.forOperation(Operation.BREACHES).stream().anyMatch(c -> c.id().equals("hibp")));
        assertTrue(r.forOperation(Operation.INFOSTEALER).stream().anyMatch(c -> c.id().equals("hudsonrock")));
        assertTrue(r.forOperation(Operation.EPSS).stream().anyMatch(c -> c.id().equals("epss")));
        assertTrue(r.forOperation(Operation.URLQUERY).stream().anyMatch(c -> c.id().equals("urlquery")));
        assertTrue(r.forOperation(Operation.DOMAIN_THREATS).stream().anyMatch(c -> c.id().equals("ransomlook")));
    }

    @Test
    void connectorsRejectInvalidInputWithoutNetwork() {
        assertFalse(new EpssSource().query(SourceQuery.of(Operation.EPSS, "no-es-cve")).isOk());
        assertFalse(new RansomLookSource().query(SourceQuery.of(Operation.DOMAIN_THREATS, "no es dominio")).isOk());
        assertFalse(new HibpSource().query(SourceQuery.of(Operation.BREACHES, "no es dominio")).isOk());
        assertFalse(new HudsonRockSource().query(SourceQuery.of(Operation.INFOSTEALER, "no es dominio")).isOk());
        assertFalse(new ShodanInternetDbSource().query(SourceQuery.of(Operation.INTERNET_DB, "999.1.1.1")).isOk());
        assertFalse(new SiteProbeSource().query(SourceQuery.of(Operation.SITE_PROBE, "no es dominio")).isOk());
        assertFalse(new RdapSource().query(SourceQuery.of(Operation.WHOIS, "999.1.1.1")).isOk());
    }

    @Test
    void parsersHandleRealFormats() {
        assertEquals("apache http_server 2.4.7",
                ShodanInternetDbSource.readableCpe("cpe:/a:apache:http_server:2.4.7"));
        assertEquals(0, SiteProbeSource.mmh3(""));
        assertNotEquals(SiteProbeSource.mmh3("a"), SiteProbeSource.mmh3("b"));
        assertEquals(Set.of("example.org"), SiteProbeSource.linkedHosts(
                "<a href=\"https://example.org/x\">x</a><a href=\"/local\">l</a>",
                URI.create("https://osde.com.ar/"), "osde.com.ar"));
    }

    @Test
    void mitreAttackBundleLoads() {
        MitreAttackSource attack = new MitreAttackSource();
        assertEquals(14, attack.getTactics().size());
        assertTrue(attack.getTechniques().size() >= 75, "tecnicas embebidas: " + attack.getTechniques().size());
        assertTrue(attack.getGroups().size() >= 13);

        SourceResult r = attack.query(SourceQuery.of(Operation.TECHNIQUE_INFO, "T1566"));
        assertTrue(r.isOk());
        assertTrue(r.getEntities().stream().anyMatch(e -> e.getType() == EntityType.ATTCK_TECHNIQUE));
        // grupos que usan phishing
        assertTrue(r.getEntities().stream().anyMatch(e -> e.getType() == EntityType.APT));

        SourceResult g = attack.query(SourceQuery.of(Operation.GROUP_INFO, "APT28"));
        assertTrue(g.isOk());
        assertTrue(g.getEntities().stream().anyMatch(e -> e.getValue().equals("APT28")));
        assertTrue(g.getEntities().stream().anyMatch(e -> e.getType() == EntityType.ATTCK_TECHNIQUE));

        // alias lookup
        SourceResult alias = attack.query(SourceQuery.of(Operation.GROUP_INFO, "Fancy Bear"));
        assertTrue(alias.getEntities().stream().anyMatch(e -> e.getValue().equals("APT28")));
    }

    @Test
    void apiKeyManagerEncryptsAtRest() {
        assertTrue(ApiKeyManager.selfTest());
    }

    @Test
    void dohRejectsInvalidDomains() {
        DnsOverHttpsSource doh = new DnsOverHttpsSource();
        SourceResult r = doh.query(SourceQuery.of(Operation.DNS_A, "no es un dominio"));
        assertFalse(r.isOk());
    }

    @Test
    void connectorsDeclareOperations() {
        for (SourceConnector c : SourcesBootstrap.defaults()) {
            assertNotNull(c.supports());
            assertFalse(c.supports().isEmpty(), c.id() + " sin operaciones");
            assertNotNull(c.name());
            assertNotNull(c.description());
        }
    }
}
