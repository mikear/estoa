package com.orion.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Puntuacion de riesgo compuesta (0-100) con desglose de factores para
 * justificar la valoracion ante el analista.
 */
public class RiskScore {

    public enum Level { LOW("Bajo", "#3FB950"), MEDIUM("Medio", "#D29922"),
                        HIGH("Alto", "#F0883E"), CRITICAL("Crítico", "#F85149");

        private final String label;
        private final String color;
        Level(String label, String color) { this.label = label; this.color = color; }
        public String label() { return label; }
        public String color() { return color; }
    }

    private double total;
    private final List<Factor> factors = new ArrayList<>();

    public record Factor(String name, double weight, double contribution, String detail) {}

    public void addFactor(String name, double weight, double contribution, String detail) {
        factors.add(new Factor(name, weight, contribution, detail));
        total += contribution;
    }

    public double getTotal() { return Math.max(0, Math.min(100, total)); }

    public Level getLevel() {
        double t = getTotal();
        if (t >= 80) return Level.CRITICAL;
        if (t >= 60) return Level.HIGH;
        if (t >= 35) return Level.MEDIUM;
        return Level.LOW;
    }

    public List<Factor> getFactors() { return factors; }
}
