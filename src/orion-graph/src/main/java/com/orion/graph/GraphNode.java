package com.orion.graph;

import com.orion.core.model.OrionEntity;

/**
 * Nodo visual del grafo. Encapsula la entidad de dominio junto con su estado
 * fisico (posicion, velocidad) y estado visual (fijado, cluster, destacado).
 */
public class GraphNode {

    private final OrionEntity entity;
    private double x, y;
    private double vx, vy;
    private boolean fixed;
    private boolean highlighted;
    private int cluster = -1;

    public GraphNode(OrionEntity entity, double x, double y) {
        this.entity = entity;
        this.x = x;
        this.y = y;
    }

    public GraphNode(OrionEntity entity) {
        this(entity, 0, 0);
    }

    /** Caja de colision del nodo (icono + etiqueta debajo). */
    public double boxWidth() {
        String l = entity.getLabel() == null ? "" : entity.getLabel();
        return Math.max(64, Math.min(190, 10 + l.length() * 6.6));
    }

    public double boxHeight() { return 58; }

    public double radius() {
        return 10 + Math.min(8, entity.getRiskScore() / 14);
    }

    public OrionEntity getEntity() { return entity; }
    public String getKey() { return entity.key(); }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }
    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }
    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }
    public boolean isHighlighted() { return highlighted; }
    public void setHighlighted(boolean h) { this.highlighted = h; }
    public int getCluster() { return cluster; }
    public void setCluster(int cluster) { this.cluster = cluster; }
}
