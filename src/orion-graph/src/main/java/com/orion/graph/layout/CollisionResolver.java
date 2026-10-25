package com.orion.graph.layout;

import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolvedor de solapamientos: separa cajas AABB de nodos que colisionan,
 * empujandolas a lo largo del eje de menor penetracion. Se aplica como paso
 * final de cada layout para garantizar legibilidad de las etiquetas.
 */
public final class CollisionResolver {

    private CollisionResolver() {}

    /** Numero de iteraciones de relajacion recomendado. */
    public static void resolve(OrionGraph g, int iterations) {
        List<GraphNode> ns = new ArrayList<>(g.getNodes());
        for (int it = 0; it < iterations; it++) {
            boolean moved = false;
            for (int i = 0; i < ns.size(); i++) {
                for (int j = i + 1; j < ns.size(); j++) {
                    if (separate(ns.get(i), ns.get(j))) moved = true;
                }
            }
            if (!moved) break;
        }
    }

    private static boolean separate(GraphNode a, GraphNode b) {
        double aw = a.boxWidth() / 2 + 8, ah = a.boxHeight() / 2 + 8;
        double bw = b.boxWidth() / 2 + 8, bh = b.boxHeight() / 2 + 8;
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double px = aw + bw - Math.abs(dx);
        double py = ah + bh - Math.abs(dy);
        if (px <= 0 || py <= 0) return false;
        if (px < py) {
            double sign = dx == 0 ? 1 : Math.signum(dx);
            if (!a.isFixed()) a.setX(a.getX() - sign * px / 2);
            if (!b.isFixed()) b.setX(b.getX() + sign * px / 2);
        } else {
            double sign = dy == 0 ? 1 : Math.signum(dy);
            if (!a.isFixed()) a.setY(a.getY() - sign * py / 2);
            if (!b.isFixed()) b.setY(b.getY() + sign * py / 2);
        }
        return true;
    }

    /** Cuenta pares de nodos cuyas cajas se solapan (metrica de QA). */
    public static int countOverlaps(OrionGraph g) {
        List<GraphNode> ns = new ArrayList<>(g.getNodes());
        int count = 0;
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                double dx = Math.abs(ns.get(j).getX() - ns.get(i).getX());
                double dy = Math.abs(ns.get(j).getY() - ns.get(i).getY());
                if (dx < (ns.get(i).boxWidth() + ns.get(j).boxWidth()) / 2
                        && dy < (ns.get(i).boxHeight() + ns.get(j).boxHeight()) / 2) {
                    count++;
                }
            }
        }
        return count;
    }
}
