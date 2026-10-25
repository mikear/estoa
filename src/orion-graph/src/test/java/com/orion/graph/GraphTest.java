package com.orion.graph;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.graph.layout.CollisionResolver;
import com.orion.graph.layout.LayoutType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    private OrionGraph buildSample() {
        OrionGraph g = new OrionGraph();
        OrionEntity d = OrionEntity.of(EntityType.DOMAIN, "evil-example.com");
        g.addEntity(d);
        for (int i = 1; i <= 5; i++) {
            g.addEdge(d, OrionEntity.of(EntityType.IPV4, "185.220.10" + i + ".5"),
                    RelationshipType.RESOLVES_TO, "resuelve a");
        }
        g.addEdge(OrionEntity.of(EntityType.IPV4, "185.220.101.5"),
                OrionEntity.of(EntityType.ASN, "AS9009"), RelationshipType.BELONGS_TO, "pertenece a");
        return g;
    }

    @Test
    void eightLayoutsAvailable() {
        assertEquals(8, LayoutType.values().length);
        for (LayoutType t : LayoutType.values()) assertNotNull(t.create(), "layout sin implementar: " + t);
    }

    @Test
    void nodesAreDeduplicatedByKey() {
        OrionGraph g = buildSample();
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "EVIL-Example.com"));
        assertEquals(7, g.nodeCount());
    }

    @Test
    void edgesAreDeduplicated() {
        OrionGraph g = buildSample();
        int before = g.edgeCount();
        g.addEdge(OrionEntity.of(EntityType.DOMAIN, "evil-example.com"),
                OrionEntity.of(EntityType.IPV4, "185.220.101.5"),
                RelationshipType.RESOLVES_TO, "resuelve a");
        assertEquals(before, g.edgeCount());
    }

    @Test
    void forceDirectedLayoutProducesFinitePositions() {
        OrionGraph g = buildSample();
        LayoutType.FORCE_DIRECTED.create().apply(g, 1200, 800);
        for (GraphNode n : g.getNodes()) {
            assertTrue(Double.isFinite(n.getX()) && Double.isFinite(n.getY()), "NaN en layout");
            assertTrue(n.getX() > -500 && n.getX() < 1700, "fuera de rango X: " + n.getX());
        }
    }

    @Test
    void collisionResolverReducesOverlaps() {
        OrionGraph g = buildSample();
        for (GraphNode n : g.getNodes()) {
            n.setX(400);
            n.setY(300);
        }
        int before = CollisionResolver.countOverlaps(g);
        CollisionResolver.resolve(g, 30);
        assertTrue(CollisionResolver.countOverlaps(g) < before);
    }

    @Test
    void removingEdgeUpdatesAdjacencyWithoutRemovingNode() {
        OrionGraph g = buildSample();
        GraphEdge edge = g.edgesOf("DOMAIN:evil-example.com").get(0);
        assertTrue(g.removeEdge(edge));
        assertFalse(g.removeEdge(edge));
        assertEquals(5, g.edgeCount());
        assertEquals(7, g.nodeCount());
        assertFalse(g.neighbors("DOMAIN:evil-example.com").contains(edge.other(edge.getSource()).getKey()));
    }

    @Test
    void clusterDetectionFindsConnectedComponent() {
        OrionGraph g = buildSample();
        List<List<GraphNode>> clusters = g.computeClusters();
        assertEquals(1, clusters.size());
        assertEquals(7, clusters.get(0).size());
    }

    @Test
    void searchFindsBySubstring() {
        OrionGraph g = buildSample();
        assertEquals(5, g.search("185.220").size());
        assertEquals(7, g.search("").size());
    }
}
