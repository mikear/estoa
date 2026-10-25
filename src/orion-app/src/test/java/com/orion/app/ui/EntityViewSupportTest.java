package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RiskScore;
import com.orion.core.model.Tlp;
import com.orion.graph.GraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityViewSupportTest {

    @Test
    void filtraPorTextoAtributosFuentesTipoTlpYRiesgo() {
        OrionEntity entity = OrionEntity.of(EntityType.DOMAIN, "cdn-update-example.ru");
        entity.attr("registrar", "ACME Registrar");
        entity.addSource("crtsh");
        entity.setTlp(Tlp.AMBER);
        entity.setRiskScore(90);
        GraphNode node = new GraphNode(entity);

        assertTrue(EntityViewSupport.matches(node, "acme", null, null, null));
        assertTrue(EntityViewSupport.matches(node, "crtsh", null, null, null));
        assertTrue(EntityViewSupport.matches(node, "dominio", null, null, null));
        assertFalse(EntityViewSupport.matches(node, "ipv4", null, null, null));
        assertTrue(EntityViewSupport.matches(node, " ", EntityType.DOMAIN, Tlp.AMBER, RiskScore.Level.CRITICAL));
        assertFalse(EntityViewSupport.matches(node, null, EntityType.DOMAIN, Tlp.GREEN, null));
        assertFalse(EntityViewSupport.matches(node, null, null, null, RiskScore.Level.HIGH));
        assertEquals("crtsh", EntityViewSupport.sources(entity));
        assertEquals(RiskScore.Level.LOW, EntityViewSupport.levelOf(0));
        assertEquals(RiskScore.Level.MEDIUM, EntityViewSupport.levelOf(35));
        assertEquals(RiskScore.Level.HIGH, EntityViewSupport.levelOf(60));
        assertEquals(RiskScore.Level.CRITICAL, EntityViewSupport.levelOf(80));
    }
}
