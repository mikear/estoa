package com.orion.persistence;

import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.graph.OrionGraph;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @Test
    void caseRoundtripPreservesGraphAndMetadata() {
        PersistenceService p = new PersistenceService(DataSourceFactory.h2Memory());

        Investigation inv = new Investigation("Campaña LockBit sector salud");
        inv.setDescription("Investigacion de intrusion con exfiltracion");
        inv.addTag("ransomware");
        inv.addTag("sector-salud");
        inv.addNote("Primera observacion del C2");

        OrionGraph g = new OrionGraph();
        OrionEntity d = OrionEntity.of(EntityType.DOMAIN, "cdn-update-example.ru");
        d.setRiskScore(72);
        d.attr("registrar", "Example Registrar");
        g.addEntity(d);
        g.addEdge(d, OrionEntity.of(EntityType.IPV4, "185.220.101.5"), RelationshipType.RESOLVES_TO, "resuelve a");
        g.addEdge(OrionEntity.of(EntityType.IPV4, "185.220.101.5"),
                OrionEntity.of(EntityType.ASN, "AS9009"), RelationshipType.BELONGS_TO, "pertenece a");
        g.addEdge(OrionEntity.of(EntityType.MALWARE, "LockBit 3.0"),
                OrionEntity.of(EntityType.HASH, "44d88612fea8a8f36de82e1278abb02f"), RelationshipType.DROPS, "despliega");

        p.save(inv, g);
        assertEquals(1, p.count());

        Optional<PersistenceService.LoadedCase> loaded = p.load(inv.getId());
        assertTrue(loaded.isPresent());
        Investigation inv2 = loaded.get().investigation();
        OrionGraph g2 = loaded.get().graph();

        assertEquals(inv.getId(), inv2.getId());
        assertEquals(inv.getName(), inv2.getName());
        assertEquals(inv.getCreatedAt(), inv2.getCreatedAt());
        assertEquals(inv.getTags(), inv2.getTags());
        assertEquals(1, inv2.getNotes().size());
        assertEquals(g.nodeCount(), g2.nodeCount());
        assertEquals(g.edgeCount(), g2.edgeCount());
        assertEquals(72, g2.getNodes().stream()
                .filter(n -> n.getEntity().getValue().equals("cdn-update-example.ru"))
                .findFirst().orElseThrow().getEntity().getRiskScore(), 0.01);
        assertTrue(g2.getNodes().stream().anyMatch(n -> n.getEntity().getAttributes().containsKey("registrar")));
    }

    @Test
    void listAndDeleteWork() {
        PersistenceService p = new PersistenceService(DataSourceFactory.h2Memory());
        Investigation a = new Investigation("Caso A");
        Investigation b = new Investigation("Caso B");
        p.save(a, new OrionGraph());
        p.save(b, new OrionGraph());
        assertEquals(2, p.listSummaries().size());
        assertTrue(p.listSummaries().stream().anyMatch(x -> x.getId().equals(a.getId())));
        assertTrue(p.delete(a.getId()));
        assertEquals(1, p.listSummaries().size());
        assertTrue(p.load(a.getId()).isEmpty());
    }

    @Test
    void saveRejectsStaleVersion() {
        PersistenceService p = new PersistenceService(DataSourceFactory.h2Memory());
        Investigation inv = new Investigation("Caso versionado");
        p.save(inv, new OrionGraph());
        Investigation otroAnalista = p.load(inv.getId()).orElseThrow().investigation();

        p.save(inv, new OrionGraph());
        assertThrows(IllegalStateException.class, () -> p.save(otroAnalista, new OrionGraph()));
    }

    @Test
    void corruptJsonDegradesGracefully() {
        PersistenceService p = new PersistenceService(DataSourceFactory.h2Memory());
        OrionGraph g = p.deserializeGraph("{\"entities\": \"no es un array\"}");
        assertNotNull(g);
        assertEquals(0, g.nodeCount());
    }
}
