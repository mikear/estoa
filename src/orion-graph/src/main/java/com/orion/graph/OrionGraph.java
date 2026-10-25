package com.orion.graph;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Grafo de investigacion. Contiene nodos indexados por identidad canonica
 * (tipo:valor) para deduplicar resultados de multiples fuentes, y aristas
 * deduplicadas por (origen, destino, relacion).
 */
public class OrionGraph {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Map<String, Set<String>> adjacency = new HashMap<>();

    /** Anade o fusiona una entidad. Devuelve el nodo resultante. */
    public GraphNode addEntity(OrionEntity entity) {
        String key = entity.key();
        GraphNode existing = nodes.get(key);
        if (existing != null) {
            mergeInto(existing.getEntity(), entity);
            return existing;
        }
        GraphNode n = new GraphNode(entity);
        nodes.put(key, n);
        adjacency.put(key, new LinkedHashSet<>());
        return n;
    }

    private void mergeInto(OrionEntity target, OrionEntity incoming) {
        incoming.getAttributes().forEach(target.getAttributes()::putIfAbsent);
        incoming.getSources().forEach(target::addSource);
        if (incoming.getRiskScore() > target.getRiskScore()) {
            target.setRiskScore(incoming.getRiskScore());
        }
        if (incoming.getFirstSeen().isBefore(target.getFirstSeen())) {
            // conservar el primer avistamiento conocido
        }
    }

    /** Anade una arista deduplicada. Crea los nodos si aun no existen. */
    public GraphEdge addEdge(OrionEntity from, OrionEntity to, RelationshipType rel, String label) {
        GraphNode a = addEntity(from);
        GraphNode b = addEntity(to);
        for (GraphEdge e : edges) {
            if (e.getSource() == a && e.getTarget() == b && e.getRelation() == rel) return e;
            if (e.getSource() == b && e.getTarget() == a && e.getRelation() == rel) return e;
        }
        GraphEdge edge = new GraphEdge(a, b, rel, label);
        edges.add(edge);
        adjacency.get(a.getKey()).add(b.getKey());
        adjacency.get(b.getKey()).add(a.getKey());
        return edge;
    }

    public boolean removeEdge(GraphEdge edge) {
        if (edge == null || !edges.remove(edge)) return false;
        String a = edge.getSource().getKey();
        String b = edge.getTarget().getKey();
        boolean stillLinked = edges.stream().anyMatch(e ->
                (e.getSource().getKey().equals(a) && e.getTarget().getKey().equals(b))
                        || (e.getSource().getKey().equals(b) && e.getTarget().getKey().equals(a)));
        if (!stillLinked) {
            Set<String> sa = adjacency.get(a);
            Set<String> sb = adjacency.get(b);
            if (sa != null) sa.remove(b);
            if (sb != null) sb.remove(a);
        }
        return true;
    }

    public boolean removeNode(String key) {
        GraphNode n = nodes.remove(key);
        if (n == null) return false;
        adjacency.remove(key);
        edges.removeIf(e -> e.getSource() == n || e.getTarget() == n);
        adjacency.values().forEach(s -> s.remove(key));
        return true;
    }

    public Optional<GraphNode> node(String key) {
        GraphNode exact = key == null ? null : nodes.get(key);
        if (exact != null) return Optional.of(exact);
        if (key == null) return Optional.empty();
        // tolerante a mayusculas: la clave canonica de la entidad es minusculas
        String lower = key.toLowerCase();
        return nodes.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals(lower))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** Busca un nodo por valor exacto sin importar el tipo. */
    public Optional<GraphNode> nodeByValue(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim().toLowerCase();
        return nodes.values().stream().filter(n -> n.getEntity().getValue().toLowerCase().equals(v)).findFirst();
    }

    public List<GraphNode> search(String text) {
        if (text == null || text.isBlank()) return List.copyOf(nodes.values());
        String t = text.trim().toLowerCase();
        return nodes.values().stream()
                .filter(n -> n.getEntity().getValue().toLowerCase().contains(t)
                        || n.getEntity().getType().label().toLowerCase().contains(t))
                .collect(Collectors.toList());
    }

    public List<GraphNode> neighbors(String key) {
        Set<String> adj = adjacency.getOrDefault(key, Set.of());
        return adj.stream().map(nodes::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public int degree(String key) { return adjacency.getOrDefault(key, Set.of()).size(); }

    public List<GraphEdge> edgesOf(String key) {
        return edges.stream().filter(e -> e.getSource().getKey().equals(key)
                || e.getTarget().getKey().equals(key)).collect(Collectors.toList());
    }

    /**
     * Detecta clustes como componentes conexas de 3 o mas nodos (union-find).
     * Asigna el identificador de cluster a cada nodo y devuelve la lista.
     */
    public List<List<GraphNode>> computeClusters() {
        Map<String, String> parent = new HashMap<>();
        nodes.keySet().forEach(k -> parent.put(k, k));
        for (GraphEdge e : edges) {
            String ra = find(parent, e.getSource().getKey());
            String rb = find(parent, e.getTarget().getKey());
            if (!ra.equals(rb)) parent.put(ra, rb);
        }
        Map<String, List<GraphNode>> groups = new LinkedHashMap<>();
        for (GraphNode n : nodes.values()) {
            groups.computeIfAbsent(find(parent, n.getKey()), k -> new ArrayList<>()).add(n);
        }
        List<List<GraphNode>> clusters = new ArrayList<>();
        int id = 0;
        for (List<GraphNode> g : groups.values()) {
            if (g.size() >= 3) {
                for (GraphNode n : g) n.setCluster(id);
                clusters.add(g);
                id++;
            } else {
                for (GraphNode n : g) n.setCluster(-1);
            }
        }
        return clusters;
    }

    private String find(Map<String, String> parent, String k) {
        String p = k;
        while (!parent.get(p).equals(p)) p = parent.get(p);
        return p;
    }

    public Map<EntityType, Long> typeCounts() {
        return nodes.values().stream()
                .collect(Collectors.groupingBy(n -> n.getEntity().getType(), Collectors.counting()));
    }

    public double averageRisk() {
        return nodes.isEmpty() ? 0 : nodes.values().stream().mapToDouble(n -> n.getEntity().getRiskScore()).average().orElse(0);
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }
    public Collection<GraphNode> getNodes() { return nodes.values(); }
    public List<GraphEdge> getEdges() { return edges; }
    public void clear() { nodes.clear(); edges.clear(); adjacency.clear(); }
}
