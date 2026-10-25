package com.orion.core.model;

/** Estado del ciclo de vida de un caso de investigacion. */
public enum InvestigationStatus {
    OPEN("Abierto"), IN_PROGRESS("En curso"), CLOSED("Cerrado"), ARCHIVED("Archivado");

    private final String label;
    InvestigationStatus(String label) { this.label = label; }
    public String label() { return label; }

    public static InvestigationStatus valueOfLabel(String label) {
        for (InvestigationStatus s : values()) {
            if (s.label().equalsIgnoreCase(label)) return s;
        }
        return OPEN;
    }
}
