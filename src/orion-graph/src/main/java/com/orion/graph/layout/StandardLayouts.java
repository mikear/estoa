package com.orion.graph.layout;

import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * Layouts deterministicos y clustes. Cada layout es una clase estatica
 * anidada disponible a traves de las fabricas {@code circular()}, etc.
 */
public final class StandardLayouts {

    private StandardLayouts() {}

    public static Layout circular() {
        return (g, w, h) -> {
            List<GraphNode> ns = new ArrayList<>(g.getNodes());
            double cx = w / 2, cy = h / 2;
            double r = Math.min(w, h) * 0.38;
            for (int i = 0; i < ns.size(); i++) {
                double angle = 2 * Math.PI * i / ns.size() - Math.PI / 2;
                ns.get(i).setX(cx + r * Math.cos(angle));
                ns.get(i).setY(cy + r * Math.sin(angle));
            }
        };
    }

    public static Layout grid() {
        return (g, w, h) -> {
            List<GraphNode> ns = new ArrayList<>(g.getNodes());
            int cols = (int) Math.ceil(Math.sqrt(ns.size() * (w / Math.max(1, h))));
            int rows = (int) Math.ceil(ns.size() / (double) Math.max(1, cols));
            double dx = (w - 120) / Math.max(1, cols);
            double dy = (h - 120) / Math.max(1, rows);
            for (int i = 0; i < ns.size(); i++) {
                int c = i % cols, r = i / cols;
                ns.get(i).setX(70 + c * dx + dx / 2);
                ns.get(i).setY(70 + r * dy + dy / 2);
            }
        };
    }

    public static Layout random() {
        return (g, w, h) -> {
            Random rnd = new Random(7);
            for (GraphNode n : g.getNodes()) {
                n.setX(60 + rnd.nextDouble() * (w - 120));
                n.setY(60 + rnd.nextDouble() * (h - 120));
            }
        };
    }

    public static Layout hierarchical() {
        return (g, w, h) -> {
            List<GraphNode> ns = new ArrayList<>(g.getNodes());
            if (ns.isEmpty()) return;
            // raices: mayor grado
            ns.sort(Comparator.comparingInt((GraphNode n) -> g.degree(n.getKey())).reversed());
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            List<List<GraphNode>> levels = new ArrayList<>();
            List<GraphNode> frontier = new ArrayList<>();
            for (GraphNode n : ns) {
                if (visited.contains(n.getKey())) continue;
                frontier.add(n);
                visited.add(n.getKey());
                levels.add(new ArrayList<>(frontier));
                frontier.clear();
                expandLevel(g, frontier, visited, levels);
                if (levels.size() > 14) break;
            }
            double dy = (h - 90) / Math.max(1, levels.size());
            for (int li = 0; li < levels.size(); li++) {
                List<GraphNode> level = levels.get(li);
                double dx = (w - 90) / Math.max(1, level.size());
                for (int i = 0; i < level.size(); i++) {
                    level.get(i).setX(60 + dx * i + dx / 2);
                    level.get(i).setY(60 + dy * li + dy / 2);
                }
            }
        };
    }

    private static void expandLevel(OrionGraph g, List<GraphNode> frontier,
                                    LinkedHashSet<String> visited, List<List<GraphNode>> levels) {
        List<GraphNode> next = new ArrayList<>();
        for (GraphNode n : new ArrayList<>(frontier)) {
            for (GraphNode nb : g.neighbors(n.getKey())) {
                if (visited.add(nb.getKey())) next.add(nb);
            }
        }
        for (GraphNode n : next) frontier.add(n);
        if (!next.isEmpty()) {
            levels.add(new ArrayList<>(next));
            expandLevel(g, frontier, visited, levels);
        }
    }

    public static Layout radial() {
        return (g, w, h) -> {
            List<GraphNode> ns = new ArrayList<>(g.getNodes());
            if (ns.isEmpty()) return;
            GraphNode root = ns.stream()
                    .max(Comparator.comparingInt(n -> g.degree(n.getKey())))
                    .orElse(ns.get(0));
            root.setX(w / 2);
            root.setY(h / 2);
            // BFS por anillos
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            visited.add(root.getKey());
            List<GraphNode> ring = new ArrayList<>(g.neighbors(root.getKey()));
            int ringIdx = 1;
            double baseR = Math.min(w, h) * 0.22;
            while (!ring.isEmpty() && ringIdx < 6) {
                double r = baseR * ringIdx;
                for (int i = 0; i < ring.size(); i++) {
                    double angle = 2 * Math.PI * i / ring.size() - Math.PI / 2;
                    ring.get(i).setX(w / 2 + r * Math.cos(angle));
                    ring.get(i).setY(h / 2 + r * Math.sin(angle));
                }
                List<GraphNode> next = new ArrayList<>();
                for (GraphNode n : ring) {
                    for (GraphNode nb : g.neighbors(n.getKey())) {
                        if (visited.add(nb.getKey())) next.add(nb);
                    }
                }
                ring = next;
                ringIdx++;
            }
        };
    }

    public static Layout cluster() {
        return (g, w, h) -> {
            List<List<GraphNode>> clusters = g.computeClusters();
            var rest = new ArrayList<GraphNode>();
            var clustered = new java.util.HashSet<String>();
            for (List<GraphNode> c : clusters) c.forEach(n -> clustered.add(n.getKey()));
            for (GraphNode n : g.getNodes()) if (!clustered.contains(n.getKey())) rest.add(n);

            int groups = clusters.size() + (rest.isEmpty() ? 0 : 1);
            if (groups == 0) return;
            double gcx = w / 2, gcy = h / 2;
            double gr = Math.min(w, h) * 0.33;
            List<List<GraphNode>> all = new ArrayList<>(clusters);
            if (!rest.isEmpty()) all.add(rest);

            for (int gi = 0; gi < all.size(); gi++) {
                double ga = 2 * Math.PI * gi / groups - Math.PI / 2;
                double ccx = gcx + gr * Math.cos(ga);
                double ccy = gcy + gr * Math.sin(ga);
                List<GraphNode> group = all.get(gi);
                double cr = Math.max(50, 28 * Math.sqrt(group.size()));
                for (int i = 0; i < group.size(); i++) {
                    double a = 2 * Math.PI * i / group.size();
                    group.get(i).setX(clampW(ccx + cr * Math.cos(a), w));
                    group.get(i).setY(clampH(ccy + cr * Math.sin(a), h));
                }
            }
        };
    }

    private static double clampW(double v, double w) { return Math.max(40, Math.min(w - 40, v)); }
    private static double clampH(double v, double h) { return Math.max(40, Math.min(h - 40, v)); }
}
