package com.orion.graph;

import com.orion.core.model.RelationshipType;

/** Arista del grafo de investigacion. */
public class GraphEdge {

    private final GraphNode source;
    private final GraphNode target;
    private final RelationshipType relation;
    private String label;
    private double strength = 1.0;
    private boolean highlighted;

    public GraphEdge(GraphNode source, GraphNode target, RelationshipType relation, String label) {
        this.source = source;
        this.target = target;
        this.relation = relation == null ? RelationshipType.RELATED_TO : relation;
        this.label = label == null || label.isBlank() ? this.relation.label() : label;
    }

    public boolean connects(String keyA, String keyB) {
        return (source.getKey().equals(keyA) && target.getKey().equals(keyB))
                || (source.getKey().equals(keyB) && target.getKey().equals(keyA));
    }

    public GraphNode other(GraphNode n) {
        return n == source ? target : source;
    }

    public GraphNode getSource() { return source; }
    public GraphNode getTarget() { return target; }
    public RelationshipType getRelation() { return relation; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getStrength() { return strength; }
    public void setStrength(double s) { this.strength = s; }
    public boolean isHighlighted() { return highlighted; }
    public void setHighlighted(boolean h) { this.highlighted = h; }
}
