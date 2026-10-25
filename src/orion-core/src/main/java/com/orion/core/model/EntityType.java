package com.orion.core.model;

/**
 * Los 28 tipos de entidad del modelo Estoa. Cada tipo define el color canonico
 * del nodo en el grafo y el icono asociado en la paleta de entidades.
 */
public enum EntityType {
    DOMAIN("Dominio", "#58A6FF", "globe"),
    IPV4("IPv4", "#F97316", "map-pin"),
    IPV6("IPv6", "#FB923C", "map-pin"),
    ASN("ASN", "#E3B341", "network"),
    NETBLOCK("Bloque de red", "#D29922", "boxes"),
    EMAIL("Correo electrónico", "#A371F7", "mail"),
    URL("URL", "#EC8CBE", "link"),
    FILE("Fichero", "#8B949E", "file"),
    HASH("Hash", "#7EE787", "fingerprint"),
    CERTIFICATE("Certificado", "#FFA657", "shield-check"),
    MALWARE("Malware", "#F85149", "bug"),
    THREAT_ACTOR("Actor de amenaza", "#D2A8FF", "user-x"),
    APT("APT", "#BC8CFF", "users"),
    CAMPAIGN("Campaña", "#FF7B72", "flag"),
    INCIDENT("Incidente", "#FF6B6B", "alert-triangle"),
    CVE("CVE", "#F0B72F", "zap"),
    EXPLOIT("Explotación", "#E36B09", "crosshair"),
    ATTCK_TECHNIQUE("Técnica ATT&CK", "#39C5CF", "crosshair"),
    ATTCK_TACTIC("Táctica ATT&CK", "#22B8CF", "layers"),
    MITIGATION("Mitigación", "#3FB950", "shield"),
    REGISTRAR("Registrador", "#9E6A03", "building"),
    ORGANIZATION("Organización", "#9198A1", "briefcase"),
    PERSON("Persona", "#C9D1D9", "user"),
    DNS_RECORD("Registro DNS", "#6BCBFF", "list"),
    PORT("Puerto", "#56D364", "plug"),
    PROTOCOL("Protocolo", "#30A14E", "share-2"),
    SOFTWARE("Software", "#8CB6FF", "package"),
    INDICATOR("Indicador", "#FFD700", "target");

    private final String label;
    private final String color;
    private final String icon;

    EntityType(String label, String color, String icon) {
        this.label = label;
        this.color = color;
        this.icon = icon;
    }

    public String label() { return label; }
    public String color() { return color; }
    public String icon() { return icon; }

    public static EntityType fromString(String s) {
        for (EntityType t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.label.equalsIgnoreCase(s)) return t;
        }
        return INDICATOR;
    }
}
