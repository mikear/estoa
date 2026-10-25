package com.orion.core.model;

/** Protocolo de manejo de informacion (TLP 2.0). */
public enum Tlp {
    CLEAR("#FFFFFF", "Uso libre"),
    GREEN("#3FB950", "Limitado a la comunidad"),
    AMBER("#D29922", "Limitado a quien deba conocerlo"),
    RED("#F85149", "Solo destinatarios específicos");

    private final String color;
    private final String description;

    Tlp(String color, String description) {
        this.color = color;
        this.description = description;
    }

    public String color() { return color; }
    public String description() { return description; }
}
