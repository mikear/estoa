package com.orion.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deteccion de comunidades por modularidad (Louvain: movimiento local +
 * agregacion). Es determinista: recorre los nodos en orden de insercion y
 * desempata por el identificador de comunidad menor.
 */
public final class CommunityDetector {

    private static final double EPS = 1e-9;
    private static final int MAX_PASSES = 50;

    private CommunityDetector() {
    }

    /** Comunidad asignada a cada clave de entidad y numero total de comunidades. */
    public record Result(Map<String, Integer> communities, int count) {
    }

    public static Result detect(OrionGraph graph) {
        List<String> keys = graph.getNodes().stream().map(GraphNode::getKey).toList();
        int n = keys.size();
        if (n == 0) return new Result(Map.of(), 0);

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(keys.get(i), i);

        List<Map<Integer, Double>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new HashMap<>());
        for (GraphEdge e : graph.getEdges()) {
            Integer a = index.get(e.getSource().getKey());
            Integer b = index.get(e.getTarget().getKey());
            if (a == null || b == null || a.equals(b)) continue;
            adj.get(a).merge(b, 1.0, Double::sum);
            adj.get(b).merge(a, 1.0, Double::sum);
        }

        int[] origToCurrent = new int[n];
        for (int i = 0; i < n; i++) origToCurrent[i] = i;

        int currentN = n;
        int[] comm = localMoving(adj);
        while (true) {
            int count = renumber(comm);
            for (int i = 0; i < n; i++) origToCurrent[i] = comm[origToCurrent[i]];
            if (count == currentN) break;

            List<Map<Integer, Double>> next = new ArrayList<>();
            for (int i = 0; i < count; i++) next.add(new HashMap<>());
            for (int u = 0; u < currentN; u++) {
                for (var entry : adj.get(u).entrySet()) {
                    int v = entry.getKey();
                    if (v < u) continue;
                    int cu = comm[u];
                    int cv = comm[v];
                    double w = entry.getValue();
                    if (cu == cv) {
                        next.get(cu).merge(cu, w, Double::sum);
                    } else {
                        next.get(cu).merge(cv, w, Double::sum);
                        next.get(cv).merge(cu, w, Double::sum);
                    }
                }
            }
            adj = next;
            currentN = count;
            comm = localMoving(adj);
        }

        Map<String, Integer> raw = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) raw.put(keys.get(i), comm[origToCurrent[i]]);

        Map<Integer, Integer> compact = new LinkedHashMap<>();
        for (int c : raw.values()) compact.putIfAbsent(c, compact.size());
        Map<String, Integer> out = new LinkedHashMap<>();
        raw.forEach((key, c) -> out.put(key, compact.get(c)));
        return new Result(Collections.unmodifiableMap(out), compact.size());
    }

    private static int[] localMoving(List<Map<Integer, Double>> adj) {
        int n = adj.size();
        int[] comm = new int[n];
        double[] degree = new double[n];
        double m2 = 0;
        for (int i = 0; i < n; i++) {
            comm[i] = i;
            double d = 2 * adj.get(i).getOrDefault(i, 0.0);
            for (var entry : adj.get(i).entrySet()) {
                if (entry.getKey() != i) d += entry.getValue();
            }
            degree[i] = d;
            m2 += d;
        }
        if (m2 <= 0) return comm;

        double[] totals = degree.clone();
        boolean changed = true;
        int pass = 0;
        while (changed && pass++ < MAX_PASSES) {
            changed = false;
            for (int i = 0; i < n; i++) {
                int old = comm[i];
                double ki = degree[i];
                totals[old] -= ki;

                Map<Integer, Double> links = new HashMap<>();
                for (var entry : adj.get(i).entrySet()) {
                    int j = entry.getKey();
                    if (j == i) continue;
                    links.merge(comm[j], entry.getValue(), Double::sum);
                }

                int best = old;
                double bestGain = links.getOrDefault(old, 0.0) - totals[old] * ki / m2;
                for (var entry : links.entrySet()) {
                    int c = entry.getKey();
                    if (c == old) continue;
                    double gain = entry.getValue() - totals[c] * ki / m2;
                    if (gain > bestGain + EPS
                            || (Math.abs(gain - bestGain) <= EPS && c < best)) {
                        best = c;
                        bestGain = gain;
                    }
                }
                if (best != old) {
                    comm[i] = best;
                    changed = true;
                }
                totals[comm[i]] += ki;
            }
        }
        return comm;
    }

    private static int renumber(int[] comm) {
        Map<Integer, Integer> ids = new LinkedHashMap<>();
        for (int c : comm) ids.putIfAbsent(c, ids.size());
        for (int i = 0; i < comm.length; i++) comm[i] = ids.get(comm[i]);
        return ids.size();
    }
}
