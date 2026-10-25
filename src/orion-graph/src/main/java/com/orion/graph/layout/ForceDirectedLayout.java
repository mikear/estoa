package com.orion.graph.layout;

import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;

import java.util.List;
import java.util.Random;

/**
 * Layout de fuerzas dirigidas con aproximacion Barnes-Hut (quadtree) para la
 * repulsion en O(n log n). Modelo fisico: repulsion de Coulomb entre todos los
 * nodos, atraccion de muelle sobre las aristas, gravedad suave hacia el centro
 * y enfriamiento progresivo de la temperatura.
 */
public class ForceDirectedLayout implements Layout {

    private double repulsion;
    private final double spring;
    private final double gravity;
    private final double damping = 0.86;
    private final double theta = 0.9;
    private double temperature = 30;
    private final Random rnd = new Random(42);
    private double kinetic = Double.MAX_VALUE;

    public ForceDirectedLayout() {
        this(1.0, 1.0, 0.02);
    }

    public ForceDirectedLayout(double repulsion, double spring, double gravity) {
        this.repulsion = repulsion;
        this.spring = spring;
        this.gravity = gravity;
    }

    @Override
    public void apply(OrionGraph graph, double width, double height) {
        temperature = 30;
        for (int i = 0; i < 260; i++) {
            boolean live = step(graph, width, height);
            if (!live) break;
        }
    }

    @Override
    public boolean step(OrionGraph graph, double width, double height) {
        List<GraphNode> ns = List.copyOf(graph.getNodes());
        if (ns.isEmpty()) return false;

        // posiciones iniciales aleatorias para nodos en el origen exacto
        for (GraphNode n : ns) {
            if (n.getX() == 0 && n.getY() == 0) {
                n.setX(width / 2 + (rnd.nextDouble() - 0.5) * width * 0.5);
                n.setY(height / 2 + (rnd.nextDouble() - 0.5) * height * 0.5);
            }
        }

        double k = Layout.idealDistance(graph, width, height) * repulsion;
        QuadTree tree = QuadTree.build(ns, width, height);

        for (GraphNode n : ns) {
            double fx = 0, fy = 0;
            // repulsion Barnes-Hut
            double[] f = tree.force(n, k, theta);
            fx += f[0];
            fy += f[1];
            // gravedad hacia el centro
            fx += (width / 2 - n.getX()) * gravity * 0.4;
            fy += (height / 2 - n.getY()) * gravity * 0.4;
            n.setVx(fx);
            n.setVy(fy);
        }

        // atraccion de muelles sobre aristas
        for (GraphEdge e : graph.getEdges()) {
            GraphNode a = e.getSource();
            GraphNode b = e.getTarget();
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double dist = Math.max(1, Math.hypot(dx, dy));
            double force = (dist - k * 1.35) * spring * 0.045;
            double ux = dx / dist * force;
            double uy = dy / dist * force;
            a.setVx(a.getVx() + ux);
            a.setVy(a.getVy() + uy);
            b.setVx(b.getVx() - ux);
            b.setVy(b.getVy() - uy);
        }

        // integracion con limite de velocidad y freno
        kinetic = 0;
        for (GraphNode n : ns) {
            if (n.isFixed()) continue;
            double vx = n.getVx() * damping;
            double vy = n.getVy() * damping;
            double vmax = temperature;
            double sp = Math.hypot(vx, vy);
            if (sp > vmax) {
                vx = vx / sp * vmax;
                vy = vy / sp * vmax;
            }
            n.setX(clamp(n.getX() + vx, 30, width - 30));
            n.setY(clamp(n.getY() + vy, 30, height - 30));
            kinetic += vx * vx + vy * vy;
        }

        temperature = Math.max(0.4, temperature * 0.985);
        return kinetic > ns.size() * 0.9 && temperature > 0.5;
    }

    public boolean isSettled() { return kinetic < 8; }

    public void setRepulsion(double repulsion) {
        this.repulsion = Math.max(0.1, repulsion);
        this.temperature = Math.max(this.temperature, 18);
    }

    public double getRepulsion() { return repulsion; }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Quadtree con centro de masa para la aproximacion Barnes-Hut. */
    static final class QuadTree {
        private double cx, cy, mass;
        private GraphNode node;
        private QuadTree[] children;
        private final double x0, y0, x1, y1;

        private QuadTree(double x0, double y0, double x1, double y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        static QuadTree build(List<GraphNode> nodes, double w, double h) {
            QuadTree root = new QuadTree(-400, -400, w + 400, h + 400);
            for (GraphNode n : nodes) root.insert(n);
            return root;
        }

        private void insert(GraphNode n) {
            if (mass == 0 && node == null && children == null) {
                node = n;
                mass = 1;
                cx = n.getX();
                cy = n.getY();
                return;
            }
            if (children == null && node != null) {
                if (node.getX() == n.getX() && node.getY() == n.getY()) return; // coincidentes
                children = new QuadTree[4];
                double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                children[0] = new QuadTree(x0, y0, mx, my);
                children[1] = new QuadTree(mx, y0, x1, my);
                children[2] = new QuadTree(x0, my, mx, y1);
                children[3] = new QuadTree(mx, my, x1, y1);
                GraphNode old = node;
                node = null;
                childFor(old).insert(old);
            }
            if (children != null) {
                double m = mass + 1;
                cx = (cx * mass + n.getX()) / m;
                cy = (cy * mass + n.getY()) / m;
                mass = m;
                childFor(n).insert(n);
            }
        }

        private QuadTree childFor(GraphNode n) {
            double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
            int idx = (n.getX() >= mx ? 1 : 0) + (n.getY() >= my ? 2 : 0);
            return children[idx];
        }

        double[] force(GraphNode target, double k, double theta) {
            double[] f = new double[2];
            accumulate(target, k, theta, f);
            return f;
        }

        private void accumulate(GraphNode t, double k, double theta, double[] f) {
            if (mass == 0) return;
            double dx = cx - t.getX();
            double dy = cy - t.getY();
            double d2 = dx * dx + dy * dy;
            if (d2 < 1e-6) d2 = 1e-6;
            double size = x1 - x0;
            if (node != null || size / Math.sqrt(d2) > theta) {
                if (node != null && node != t) {
                    double d = Math.sqrt(d2);
                    double force = (k * k) / d2 * 0.0016;
                    f[0] -= dx / d * force * 12;
                    f[1] -= dy / d * force * 12;
                } else if (children != null) {
                    for (QuadTree c : children) c.accumulate(t, k, theta, f);
                }
            } else {
                double d = Math.sqrt(d2);
                double force = (k * k) / d2 * 0.0016 * mass;
                f[0] -= dx / d * force * 12;
                f[1] -= dy / d * force * 12;
            }
        }
    }
}
