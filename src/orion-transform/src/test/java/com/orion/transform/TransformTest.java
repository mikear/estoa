package com.orion.transform;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformTest {

    @Test
    void twentyTwoTransformsRegistered() {
        assertEquals(22, TransformsBootstrap.defaults().size());
    }

    @Test
    void registryFiltersByInputType() {
        TransformRegistry r = new TransformRegistry();
        TransformsBootstrap.registerDefaults(r);
        List<Transform> forDomain = r.forInput(EntityType.DOMAIN);
        assertTrue(forDomain.size() >= 9, "transformaciones de dominio: " + forDomain.size());
        assertTrue(forDomain.stream().anyMatch(t -> t.id().equals("domain-dns")));
        assertTrue(forDomain.stream().anyMatch(t -> t.id().equals("domain-subdomains")));
        assertTrue(forDomain.stream().anyMatch(t -> t.id().equals("domain-site-probe")));
        assertTrue(forDomain.stream().anyMatch(t -> t.id().equals("domain-breaches")));
        assertTrue(forDomain.stream().anyMatch(t -> t.id().equals("domain-infostealer")));

        List<Transform> forIp = r.forInput(EntityType.IPV4);
        assertTrue(forIp.stream().anyMatch(t -> t.id().equals("ip-asn")));
        assertTrue(forIp.stream().anyMatch(t -> t.id().equals("ip-internetdb")));
        assertTrue(forIp.stream().anyMatch(t -> t.id().equals("ip-rdap")));
        assertFalse(forIp.stream().anyMatch(t -> t.id().equals("domain-whois")));

        List<Transform> forCve = r.forInput(EntityType.CVE);
        assertTrue(forCve.stream().anyMatch(t -> t.id().equals("cve-info")));

        List<Transform> forApt = r.forInput(EntityType.APT);
        assertTrue(forApt.stream().anyMatch(t -> t.id().equals("actor-profile")));
    }

    @Test
    void commercialTransformsRequireKeys() {
        List<Transform> keyed = TransformsBootstrap.defaults().stream()
                .filter(Transform::requiresApiKey).toList();
        assertEquals(4, keyed.size());
        assertTrue(keyed.stream().anyMatch(t -> t.id().equals("urlquery-reports")));
    }

    @Test
    void transformFailsGracefullyOnBadInput() {
        TransformRegistry r = new TransformRegistry();
        TransformsBootstrap.registerDefaults(r);
        // RDAP con dominio invalido: el conector devuelve error pero la transformacion no explota
        TransformResult res = r.run("domain-whois",
                OrionEntity.of(EntityType.DOMAIN, "dominio invalido!!"), TransformContext.defaults());
        assertNotNull(res);
        assertNotNull(res.getMessage());
    }

    @Test
    void attackTechniqueWorksOffline() {
        TransformRegistry r = new TransformRegistry();
        TransformsBootstrap.registerDefaults(r);
        com.orion.sources.SourceRegistry sr = new com.orion.sources.SourceRegistry();
        com.orion.sources.SourcesBootstrap.registerDefaults(sr);
        TransformResult res = r.run("attack-technique",
                OrionEntity.of(EntityType.ATTCK_TECHNIQUE, "T1566"), new TransformContext(sr));
        assertTrue(res.isOk());
        assertTrue(res.getEntities().stream().anyMatch(e -> e.getType() == EntityType.ATTCK_TACTIC));
        assertTrue(res.getEntities().stream().anyMatch(e -> e.getType() == EntityType.APT));
    }
}
