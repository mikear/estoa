package com.orion.correlation;

import com.orion.graph.OrionGraph;

import java.util.List;

/** Regla de correlacion evaluable sobre el grafo del caso. */
public abstract class CorrelationRule {

    private final String id;
    private final String name;
    private final String description;
    private volatile boolean enabled = true;

    protected CorrelationRule(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public abstract List<Finding> evaluate(OrionGraph graph);

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    protected Finding finding(double confidence, String desc, List<String> keys) {
        return new Finding(id, name, desc, confidence, keys);
    }
}
