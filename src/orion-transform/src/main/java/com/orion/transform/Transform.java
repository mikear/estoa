package com.orion.transform;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;

import java.util.Set;

/**
 * Transformacion tipo Maltego: recibe una entidad del grafo y produce nuevas
 * entidades y relaciones obtenidas de los conectores de datos.
 */
public interface Transform {

    String id();

    String name();

    String description();

    /** Grupo funcional para el menu contextual: OSINT, TI, infraestructura... */
    Category category();

    Set<EntityType> inputs();

    Set<EntityType> outputs();

    default boolean requiresApiKey() { return false; }

    TransformResult apply(OrionEntity input, TransformContext ctx);

    enum Category {
        ENRICHMENT("Enriquecimiento"),
        THREAT_INTEL("Inteligencia de amenazas"),
        INFRASTRUCTURE("Infraestructura"),
        HISTORICAL("Histórico"),
        COMMERCIAL("Comercial");

        private final String label;

        Category(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** Registro de transformaciones disponible. */
    static TransformRegistry registry() { return TransformRegistry.getInstance(); }
}
