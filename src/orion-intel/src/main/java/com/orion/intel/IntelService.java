package com.orion.intel;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.model.RiskScore;
import com.orion.core.util.NetUtil;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.persistence.PersistenceService;
import com.orion.sources.SourceRegistry;
import com.orion.sources.SourcesBootstrap;
import com.orion.transform.TransformContext;
import com.orion.transform.TransformRegistry;
import com.orion.transform.TransformResult;
import com.orion.transform.TransformsBootstrap;

import java.util.List;
import java.util.Optional;

/**
 * Servicio central de inteligencia: aplica transformaciones al grafo,
 * reescala el riesgo, gestiona el caso activo y administra la persistencia.
 */
public class IntelService {

    private final SourceRegistry sources;
    private final TransformRegistry transforms;
    private final PersistenceService persistence;

    private Investigation currentCase;
    private OrionGraph graph = new OrionGraph();

    public IntelService() {
        this(defaultPersistence());
    }

    public IntelService(PersistenceService persistence) {
        this.sources = SourceRegistry.getInstance();
        SourcesBootstrap.registerDefaults(sources);
        this.transforms = TransformRegistry.getInstance();
        TransformsBootstrap.registerDefaults(transforms);
        this.persistence = persistence;
        this.currentCase = new Investigation("Caso sin título");
    }

    private static PersistenceService defaultPersistence() {
        try {
            return new PersistenceService(com.orion.persistence.DataSourceFactory.fromEnvironment(
                    java.nio.file.Path.of(System.getProperty("user.home"), ".orion", "orion.db")));
        } catch (Exception e) {
            return new PersistenceService(com.orion.persistence.DataSourceFactory.h2Memory());
        }
    }

    // ---- caso activo ----

    public Investigation getCurrentCase() { return currentCase; }

    public void setCurrentCase(Investigation inv, OrionGraph g) {
        this.currentCase = inv == null ? new Investigation("Caso sin título") : inv;
        this.graph = g == null ? new OrionGraph() : g;
    }

    public OrionGraph getGraph() { return graph; }

    public void saveCase() {
        persistence.save(currentCase, graph);
    }

    public List<Investigation> listCases() {
        return persistence.listSummaries();
    }

    public boolean loadCase(String id) {
        Optional<PersistenceService.LoadedCase> loaded = persistence.load(id);
        loaded.ifPresent(l -> setCurrentCase(l.investigation(), l.graph()));
        return loaded.isPresent();
    }

    public boolean deleteCase(String id) {
        return persistence.delete(id);
    }

    // ---- grafo y entidades ----

    public GraphNode addObservable(String value) {
        EntityType type = NetUtil.detectType(value);
        OrionEntity e = OrionEntity.of(type, value.trim());
        return graph.addEntity(e);
    }

    public void removeEntity(String key) {
        graph.removeNode(key);
    }

    /** Aplica una transformacion y fusiona el resultado en el grafo. */
    public TransformResult applyTransform(String entityKey, String transformId) {
        Optional<GraphNode> node = graph.node(entityKey);
        if (node.isEmpty()) {
            return new TransformResult(false, transformId, "Entidad no encontrada: " + entityKey, 0);
        }
        TransformResult result = fetchTransform(node.get().getEntity(), transformId);
        mergeTransform(entityKey, result);
        return result;
    }

    /**
     * Ejecuta la transformacion sin tocar el grafo. Se puede llamar desde un
     * hilo de trabajo; el resultado se fusiona luego con {@link #mergeTransform}.
     */
    public TransformResult fetchTransform(OrionEntity input, String transformId) {
        if (input == null) return new TransformResult(false, transformId, "Entidad no encontrada", 0);
        return transforms.run(transformId, input, new TransformContext(sources));
    }

    /** Fusiona un resultado ya calculado. Solo desde el hilo de la interfaz. */
    public void mergeTransform(String entityKey, TransformResult result) {
        if (result == null) return;
        Optional<GraphNode> node = graph.node(entityKey);
        if (node.isEmpty()) return;
        mergeIntoGraph(result, node.get().getEntity());
        currentCase.touch();
    }

    private void mergeIntoGraph(TransformResult result, OrionEntity input) {
        java.util.Set<String> linked = new java.util.HashSet<>();
        for (EdgeDraft d : result.getEdges()) {
            linked.add(d.fromValue().trim().toLowerCase());
            linked.add(d.toValue().trim().toLowerCase());
        }
        for (OrionEntity e : result.getEntities()) {
            graph.addEntity(e);
            // Ninguna entidad descubierta debe quedar aislada: si la fuente no
            // aporto arista, se enlaza con la entidad de origen.
            if (!e.key().equals(input.key()) && !linked.contains(e.getValue().trim().toLowerCase())) {
                graph.addEdge(input, e, RelationshipType.RELATED_TO, "descubierto por transformación");
            }
        }
        for (EdgeDraft d : result.getEdges()) {
            OrionEntity from = resolve(d.fromValue());
            OrionEntity to = resolve(d.toValue());
            graph.addEdge(from, to, d.relation(), d.label());
        }
        rescoreAll();
    }

    private OrionEntity resolve(String value) {
        Optional<GraphNode> existing = graph.nodeByValue(value);
        if (existing.isPresent()) return existing.get().getEntity();
        return OrionEntity.of(NetUtil.detectType(value), value);
    }

    // ---- riesgo ----

    /** Recalcula la puntuacion de todas las entidades del grafo. */
    public void rescoreAll() {
        RiskScoringService scorer = new RiskScoringService();
        for (GraphNode n : graph.getNodes()) {
            RiskScore rs = scorer.score(n.getEntity(), graph);
            n.getEntity().setRiskScore(rs.getTotal());
        }
    }

    public RiskScore score(String entityKey) {
        Optional<GraphNode> n = graph.node(entityKey);
        return n.isEmpty() ? new RiskScore()
                : new RiskScoringService().score(n.get().getEntity(), graph);
    }

    public SourceRegistry sources() { return sources; }
    public TransformRegistry transforms() { return transforms; }
    public PersistenceService persistence() { return persistence; }
}
