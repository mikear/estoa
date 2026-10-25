package com.orion.core;

import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.util.NetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreModelTest {

    @Test
    void modelContainsExactly28EntityTypes() {
        assertEquals(28, EntityType.values().length);
    }

    @Test
    void investigationDoesNotLeakOsUser() {
        assertEquals("analista", new Investigation("Prueba").getAnalyst());
    }

    @Test
    void entityKeyIsCanonical() {
        OrionEntity e = OrionEntity.of(EntityType.DOMAIN, "Evil-Example.COM");
        assertEquals("DOMAIN:evil-example.com", e.key());
        assertEquals("Evil-Example.COM", e.getLabel());
    }

    @Test
    void riskScoreIsClamped() {
        OrionEntity e = OrionEntity.of(EntityType.IPV4, "1.2.3.4");
        e.setRiskScore(150);
        assertEquals(100, e.getRiskScore());
        e.setRiskScore(-5);
        assertEquals(0, e.getRiskScore());
    }

    @Test
    void detectTypeRecognizesObservables() {
        assertEquals(EntityType.IPV4, NetUtil.detectType("185.220.101.4"));
        assertEquals(EntityType.DOMAIN, NetUtil.detectType("portal.example.es"));
        assertEquals(EntityType.HASH, NetUtil.detectType("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        assertEquals(EntityType.URL, NetUtil.detectType("https://login.evil.io/phish"));
        assertEquals(EntityType.EMAIL, NetUtil.detectType("operador@evil.io"));
        assertEquals(EntityType.CVE, NetUtil.detectType("CVE-2024-21412"));
        assertEquals(EntityType.ASN, NetUtil.detectType("AS9009"));
    }

    @Test
    void registrableDomainHeuristic() {
        assertEquals("example.co.uk", NetUtil.registrableDomain("www.mail.example.co.uk"));
        assertEquals("example.es", NetUtil.registrableDomain("a.b.example.es"));
    }

    @Test
    void levenshteinDistance() {
        assertEquals(1, NetUtil.levenshtein("microsoft", "micros0ft"));
        assertEquals(2, NetUtil.levenshtein("microsoft", "rnicrosoft"));
        assertEquals(0, NetUtil.levenshtein("abc", "abc"));
    }
}
