package com.orion.graph;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityDetectorTest {

    @Test
    void separatesTwoTrianglesJoinedByOneEdge() {
        OrionGraph g = new OrionGraph();
        for (int i = 0; i < 3; i++) {
            g.addEntity(OrionEntity.of(EntityType.DOMAIN, "a" + i + ".example"));
        }
        for (int i = 0; i < 3; i++) {
            g.addEntity(OrionEntity.of(EntityType.DOMAIN, "b" + i + ".example"));
        }
        connect(g, "a0", "a1");
        connect(g, "a1", "a2");
        connect(g, "a2", "a0");
        connect(g, "b0", "b1");
        connect(g, "b1", "b2");
        connect(g, "b2", "b0");
        connect(g, "a0", "b0");

        CommunityDetector.Result result = CommunityDetector.detect(g);
        assertEquals(2, result.count());
        Map<String, Integer> c = result.communities();
        assertEquals(c.get("DOMAIN:a0.example"), c.get("DOMAIN:a1.example"));
        assertEquals(c.get("DOMAIN:a0.example"), c.get("DOMAIN:a2.example"));
        assertEquals(c.get("DOMAIN:b0.example"), c.get("DOMAIN:b1.example"));
        assertEquals(c.get("DOMAIN:b0.example"), c.get("DOMAIN:b2.example"));
        assertNotEquals(c.get("DOMAIN:a0.example"), c.get("DOMAIN:b0.example"));
    }

    @Test
    void isolatedNodesStayIndependent() {
        OrionGraph g = new OrionGraph();
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "one.example"));
        g.addEntity(OrionEntity.of(EntityType.DOMAIN, "two.example"));

        CommunityDetector.Result result = CommunityDetector.detect(g);
        assertEquals(2, result.count());
        assertNotEquals(result.communities().get("DOMAIN:one.example"),
                result.communities().get("DOMAIN:two.example"));
    }

    @Test
    void emptyGraphHasNoCommunities() {
        CommunityDetector.Result result = CommunityDetector.detect(new OrionGraph());
        assertEquals(0, result.count());
        assertTrue(result.communities().isEmpty());
    }

    private void connect(OrionGraph g, String from, String to) {
        g.addEdge(OrionEntity.of(EntityType.DOMAIN, from + ".example"),
                OrionEntity.of(EntityType.DOMAIN, to + ".example"),
                RelationshipType.RELATED_TO, "relacionado");
    }
}
