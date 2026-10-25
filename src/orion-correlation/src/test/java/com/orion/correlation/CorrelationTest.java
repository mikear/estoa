package com.orion.correlation;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.graph.OrionGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationTest {

    @Test
    void eightRulesAvailable() {
        assertEquals(8, new CorrelationEngine().getRules().size());
    }

    @Test
    void sharedAsnTriggersOnThreeMaliciousIps() {
        OrionGraph g = new OrionGraph();
        for (int i = 1; i <= 3; i++) {
            OrionEntity ip = OrionEntity.of(EntityType.IPV4, "185.220.101." + i);
            ip.setRiskScore(80);
            ip.addSource("feodo");
            g.addEntity(ip);
            g.addEdge(ip, OrionEntity.of(EntityType.ASN, "AS9009"), RelationshipType.BELONGS_TO, "pertenece a");
        }
        CorrelationEngine.CorrelationReport r = new CorrelationEngine().run(g);
        assertTrue(r.findingsByRule("shared-asn") >= 1, "findings: " + r.findings());
        assertTrue(r.hypotheses().stream().anyMatch(h -> h.getRuleId().equals("shared-asn")));
    }

    @Test
    void domainSimilarityDetectsTyposquatting() {
        OrionGraph g = new OrionGraph();
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "micros0ft-login.com"));
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "microsoft-login.com"));
        CorrelationEngine.CorrelationReport r = new CorrelationEngine().run(g);
        assertTrue(r.findingsByRule("domain-similarity") >= 1);
    }

    @Test
    void multiSourceConfirmationRequiresTwoSourcesAndRisk() {
        OrionGraph g = new OrionGraph();
        OrionEntity ip = OrionEntity.of(EntityType.IPV4, "45.155.148.10");
        ip.setRiskScore(85);
        ip.addSource("threatfox");
        ip.addSource("feodo");
        g.addEntity(ip);
        CorrelationEngine.CorrelationReport r = new CorrelationEngine().run(g);
        assertTrue(r.findingsByRule("multi-source") >= 1);
        assertEquals(Hypothesis.Status.PENDING, r.hypotheses().get(0).getStatus());

        OrionEntity confirmed = OrionEntity.of(EntityType.IPV4, "45.155.148.99");
        confirmed.setRiskScore(90);
        confirmed.addSource("threatfox");
        confirmed.addSource("feodo");
        confirmed.addSource("urlhaus");
        g.addEntity(confirmed);
        CorrelationEngine.CorrelationReport r2 = new CorrelationEngine().run(g);
        assertTrue(r2.hypotheses().stream()
                .filter(h -> h.getRuleId().equals("multi-source"))
                .anyMatch(h -> h.getStatus() == Hypothesis.Status.CONFIRMED));
    }

    @Test
    void disabledRulesAreSkipped() {
        OrionGraph g = new OrionGraph();
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "aaaa-example.com"));
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "aaab-example.com"));
        CorrelationEngine engine = new CorrelationEngine();
        engine.setEnabled("domain-similarity", false);
        CorrelationEngine.CorrelationReport r = engine.run(g);
        assertEquals(0, r.findingsByRule("domain-similarity"));
    }

    @Test
    void ttpOverlapDetectsPotentialAlias() {
        OrionGraph g = new OrionGraph();
        OrionEntity a = OrionEntity.of(EntityType.APT, "APT28");
        OrionEntity b = OrionEntity.of(EntityType.APT, "Fancy Bear Group");
        g.addEntity(a);
        g.addEntity(b);
        for (String t : List.of("T1566", "T1203", "T1059.001")) {
            OrionEntity tech = OrionEntity.of(EntityType.ATTCK_TECHNIQUE, t);
            g.addEntity(tech);
            g.addEdge(a, tech, RelationshipType.USES_TECHNIQUE, "usa tecnica");
            g.addEdge(b, tech, RelationshipType.USES_TECHNIQUE, "usa tecnica");
        }
        CorrelationEngine.CorrelationReport r = new CorrelationEngine().run(g);
        assertTrue(r.findingsByRule("ttp-overlap") >= 1);
    }
}
