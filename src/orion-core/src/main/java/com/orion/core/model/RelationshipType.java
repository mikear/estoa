package com.orion.core.model;

/** Tipos de relacion (aristas) del grafo de investigacion. */
public enum RelationshipType {
    RESOLVES_TO("resuelve a"),
    REVERSE_RESOLVES("resuelve inversamente a"),
    BELONGS_TO("pertenece a"),
    REGISTRAR_OF("registrador de"),
    HAS_CERTIFICATE("certificado por"),
    CERTIFICATE_COVERS("cubre a"),
    HOSTS("aloja"),
    COMMUNICATES_WITH("se comunica con"),
    INFECTED_BY("infectado por"),
    DROPS("despliega"),
    EXPLOITS("explota"),
    USES_TECHNIQUE("usa técnica"),
    ATTRIBUTED_TO("atribuido a"),
    MEMBER_OF("miembro de"),
    ALIAS_OF("alias de"),
    DOWNLOADS("descarga"),
    VARIANT_OF("variante de"),
    OBSERVED_IN("observado en"),
    MITIGATES("mitiga"),
    LOCATED_IN("localizado en"),
    OPERATES("opera"),
    TARGETS("objetivo de"),
    RELATED_TO("relacionado con");

    private final String label;

    RelationshipType(String label) { this.label = label; }

    public String label() { return label; }
}
