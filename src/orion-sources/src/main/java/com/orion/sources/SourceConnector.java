package com.orion.sources;

/**
 * Conector de datos (abierto o comercial). Implementa una fuente concreta de
 * inteligencia y traduce sus respuestas al modelo de entidades Estoa.
 */
public interface SourceConnector {

    /** Identificador corto, p. ej. {@code dns-doh}. */
    String id();

    /** Nombre visible del conector. */
    String name();

    String description();

    /** Categoria: OSINT, TI, infraestructura o comercial. */
    Category category();

    /** True si el conector necesita clave de API configurada. */
    boolean requiresApiKey();

    /** Nombre de la variable/clave de API esperada. */
    default String apiKeyName() { return id().toUpperCase().replace('-', '_') + "_KEY"; }

    /** Operaciones que este conector sabe resolver. */
    java.util.Set<Operation> supports();

    /**
     * Ejecuta una consulta. Nunca lanza excepciones: ante fallo devuelve
     * SourceResult con ok=false y el mensaje explicativo.
     */
    SourceResult query(SourceQuery query);

    enum Category { OPEN, COMMERCIAL }

    /** Registro de conectores disponibles. */
    static SourceRegistry registry() { return SourceRegistry.getInstance(); }
}
