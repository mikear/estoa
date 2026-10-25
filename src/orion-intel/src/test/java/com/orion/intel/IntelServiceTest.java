package com.orion.intel;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.graph.OrionGraph;
import com.orion.transform.BaseTransform;
import com.orion.transform.Transform;
import com.orion.transform.TransformContext;
import com.orion.transform.TransformResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntelServiceTest {

    @Test
    void addObservableAutoDetectsType() {
        IntelService svc = newTestService();
        svc.addObservable("185.220.101.5");
        svc.addObservable("phish-example.ru");
        assertEquals(2, svc.getGraph().nodeCount());
        assertTrue(svc.getGraph().node("IPV4:185.220.101.5").isPresent());
        assertTrue(svc.getGraph().node("DOMAIN:phish-example.ru").isPresent());
    }

    @Test
    void attackTechniqueTransformPopulatesGraph() {
        IntelService svc = newTestService();
        svc.addObservable("T1566");
        var result = svc.applyTransform("ATTCK_TECHNIQUE:T1566", "attack-technique");
        assertTrue(result.isOk());
        assertTrue(result.getEntities().size() >= 3); // tecnica + tactica + actores
        assertTrue(svc.getGraph().getNodes().stream()
                .anyMatch(n -> n.getEntity().getType() == EntityType.ATTCK_TACTIC));
        assertTrue(svc.getGraph().getNodes().stream()
                .anyMatch(n -> n.getEntity().getType() == EntityType.APT));
        assertTrue(svc.getGraph().edgeCount() >= 3);
    }

    @Test
    void actorProfileTransformBuildsTtpProfile() {
        IntelService svc = newTestService();
        svc.getGraph().addEntity(com.orion.core.model.OrionEntity.of(EntityType.APT, "APT28"));
        var result = svc.applyTransform("APT:APT28", "actor-profile");
        assertTrue(result.isOk());
        AttackMatrixService matrix = new AttackMatrixService();
        List<AttackMatrixService.ActorProfile> profiles = matrix.actorProfiles(svc.getGraph());
        assertEquals(1, profiles.size());
        assertEquals("APT28", profiles.get(0).name());
        assertTrue(profiles.get(0).techniques() >= 5);
        assertTrue(matrix.coverage(svc.getGraph()) > 0);
        assertEquals(14, matrix.matrix(svc.getGraph()).size());
    }

    @Test
    void transformEntitiesWithoutSourceEdgeStayConnected() {
        IntelService svc = newTestService();
        svc.transforms().register(new BaseTransform("test-orphan", "Prueba", "Entidad sin arista",
                Transform.Category.ENRICHMENT, Set.of(EntityType.DOMAIN), Set.of(EntityType.DOMAIN), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return new TransformResult(true, "test-orphan", "ok", 0)
                        .entity(OrionEntity.of(EntityType.DOMAIN, "orphan.example.org"));
            }
        });
        svc.addObservable("example.com");
        svc.applyTransform("DOMAIN:example.com", "test-orphan");
        assertTrue(svc.getGraph().node("DOMAIN:orphan.example.org").isPresent());
        assertEquals(1, svc.getGraph().degree("DOMAIN:example.com"));
        assertEquals(1, svc.getGraph().edgeCount());
    }

    @Test
    void riskScoringWeightsFactors() {
        OrionGraph g = new OrionGraph();
        OrionEntity ip = OrionEntity.of(EntityType.IPV4, "45.155.148.10");
        ip.setRiskScore(70);
        ip.addSource("feodo");
        ip.addSource("threatfox");
        ip.attr("feodoC2", true);
        g.addEntity(ip);
        g.addEdge(ip, OrionEntity.of(EntityType.ASN, "AS200000"), RelationshipType.BELONGS_TO, "x");
        RiskScoringService scorer = new RiskScoringService();
        var rs = scorer.score(ip, g);
        assertTrue(rs.getTotal() >= 60, "riesgo total: " + rs.getTotal());
        assertTrue(rs.getFactors().size() == 4);
        assertEquals(com.orion.core.model.RiskScore.Level.HIGH, rs.getLevel());
    }

    @Test
    void cveRiskCombinesCvssEpssAndKev() {
        OrionGraph g = new OrionGraph();
        OrionEntity cve = OrionEntity.of(EntityType.CVE, "CVE-2024-0001");
        cve.addSource("nvd");
        cve.addSource("cisa-kev");
        cve.addSource("epss");
        cve.attr("cvss", 9.8);
        cve.attr("epss", 0.72);
        cve.attr("kev", true);
        g.addEntity(cve);
        g.addEdge(cve, OrionEntity.of(EntityType.DOMAIN, "example.com"), RelationshipType.EXPLOITS, "x");

        RiskScoringService scorer = new RiskScoringService();
        var rs = scorer.score(cve, g);
        assertTrue(rs.getTotal() >= 80, "riesgo CVE: " + rs.getTotal());
        assertEquals(com.orion.core.model.RiskScore.Level.CRITICAL, rs.getLevel());
    }

    @Test
    void benignEnrichmentDoesNotInflateRisk() {
        OrionGraph g = new OrionGraph();
        OrionEntity domain = OrionEntity.of(EntityType.DOMAIN, "benign.example");
        domain.addSource("dns");
        domain.addSource("whois");
        domain.addSource("crtsh");
        g.addEntity(domain);

        RiskScoringService scorer = new RiskScoringService();
        var rs = scorer.score(domain, g);
        assertEquals(30.0, rs.getTotal(), 0.01);
        assertEquals(com.orion.core.model.RiskScore.Level.LOW, rs.getLevel());
    }

    @Test
    void casePersistenceRoundtrip() {
        IntelService svc = newTestService();
        svc.getCurrentCase().setName("Caso de prueba");
        svc.addObservable("evil.example.com");
        svc.saveCase();
        assertTrue(svc.listCases().stream().anyMatch(c -> c.getName().equals("Caso de prueba")));
    }

    private IntelService newTestService() {
        return new IntelService(new com.orion.persistence.PersistenceService(
                com.orion.persistence.DataSourceFactory.h2Memory()));
    }
}
