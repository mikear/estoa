package com.orion.core.model;

/** Prioridad del caso. */
public enum Priority {
    LOW("Baja", "#3FB950"),
    MEDIUM("Media", "#D29922"),
    HIGH("Alta", "#F0883E"),
    CRITICAL("Crítica", "#F85149");

    private final String label;
    private final String color;

    Priority(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public String color() { return color; }

    public static Priority valueOfLabel(String label) {
        for (Priority p : values()) {
            if (p.label().equalsIgnoreCase(label)) return p;
        }
        return MEDIUM;
    }
}
