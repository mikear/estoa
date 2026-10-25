package com.orion.sources;

/**
 * Operaciones que los conectores pueden resolver. Cada conector declara
 * cuales soporta y el motor de transformacion enruta la peticion.
 */
public enum Operation {
    DNS_A("Registros A"),
    DNS_AAAA("Registros AAAA"),
    DNS_MX("Registros MX"),
    DNS_NS("Servidores NS"),
    DNS_TXT("Registros TXT"),
    WHOIS("WHOIS / RDAP"),
    SUBDOMAINS("Subdominios"),
    CERTIFICATES("Certificados"),
    IP_GEO("Geolocalización IP"),
    IP_ASN("ASN de la IP"),
    IP_REPUTATION("Reputación de la IP"),
    HASH_INFO("Información de hash"),
    URL_REPUTATION("Reputación de URL"),
    DOMAIN_THREATS("Amenazas del dominio"),
    CVE_INFO("Detalle de vulnerabilidad"),
    KEV_CHECK("CVE explotado (KEV)"),
    ACTOR_INFO("Perfil de actor"),
    TECHNIQUE_INFO("Técnica ATT&CK"),
    GROUP_INFO("Grupo de amenaza"),
    RECENT_VICTIMS("Víctimas recientes"),
    SCAN_INFO("Análisis de URL"),
    SHODAN_HOST("Exposición del equipo"),
    INTERNET_DB("Exposición sin clave (InternetDB)"),
    SITE_PROBE("Inspección del sitio web"),
    BREACHES("Filtraciones de datos"),
    INFOSTEALER("Credenciales infostealer"),
    EPSS("Probabilidad de explotación (EPSS)"),
    URLQUERY("Reportes urlquery"),
    VT_REPORT("Informe agregado");

    private final String label;

    Operation(String label) { this.label = label; }

    public String label() { return label; }
}
