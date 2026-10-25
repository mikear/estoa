package com.orion.reports;

/**
 * Los 7 tipos de informe de Estoa.
 */
public enum ReportType {
    EXECUTIVE_SUMMARY("Resumen ejecutivo", "Visión de alto nivel con métricas y recomendaciones para dirección."),
    CASE_SUMMARY("Resumen de caso", "Inventario completo de entidades, relaciones y notas del caso."),
    IOC_REPORT("Informe de IOCs", "Indicadores de compromiso priorizados con fuentes y confianza."),
    THREAT_ACTOR_PROFILE("Perfil de actor", "Actores identificados con alias, origen y TTPs documentadas."),
    CAMPAIGN_TIMELINE("Línea temporal de campaña", "Cronología de eventos y observaciones del caso."),
    ATTACK_SURFACE("Superficie de ataque", "Activos expuestos: dominios, IPs, puertos y certificados."),
    TECHNICAL_INVESTIGATION("Informe técnico de investigación", "Detalle completo: grafo, hallazgos, hipótesis y matriz ATT&CK.");

    private final String label;
    private final String description;

    ReportType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
