package com.orion.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orion.correlation.CorrelationEngine;
import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.intel.AttackMatrixService;
import com.orion.intel.IntelService;
import com.orion.intel.RiskScoringService;
import com.orion.persistence.DataSourceFactory;
import com.orion.persistence.PersistenceService;
import com.orion.reports.ReportFormat;
import com.orion.reports.ReportGenerator;
import com.orion.reports.ReportModel;
import com.orion.reports.ReportType;
import com.orion.sources.SourceConnector;
import com.orion.transform.Transform;
import com.orion.transform.TransformResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verificación con datos reales. Ejecuta el mismo {@link IntelService},
 * registro de conectores y transformaciones que la interfaz sobre los
 * observables que aporta el operador (nunca sobre el caso de demostración),
 * mide cada transformación, comprueba persistencia, correlación e informes y
 * deja una transcripción JSON como evidencia.
 *
 * <p>Uso:
 * <pre>
 *   java -jar estoa.jar --live &lt;observable&gt;... [--out DIR]
 *   java -jar estoa.jar --live APT=APT28 T1566 CVE-2021-44228 --out evidencias
 * </pre>
 *
 * <p>Un observable puede forzar su tipo con la forma {@code TIPO=valor}
 * (por ejemplo {@code APT=APT28}); si no, se detecta con
 * {@link com.orion.core.util.NetUtil#detectType(String)}.
 */
public final class LiveProbe {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private LiveProbe() {
    }

    public static int run(String[] args) {
        Options options = parse(args);
        if (options.observables().isEmpty()) {
            System.out.println("Uso: java -jar estoa.jar --live <observable>... [--out DIR]");
            return 2;
        }

        Instant started = Instant.now();
        PersistenceService persistence = new PersistenceService(DataSourceFactory.h2Memory());
        IntelService intel = new IntelService(persistence);
        Investigation investigation = new Investigation("Verificación con datos reales " + STAMP.format(started));
        investigation.setDescription("Caso creado por --live con observables aportados por el operador; "
                + "sin datos de demostración.");
        OrionGraph graph = new OrionGraph();
        intel.setCurrentCase(investigation, graph);

        Map<String, Object> transcript = new LinkedHashMap<>();
        transcript.put("mode", "live");
        transcript.put("startedAt", started.toString());
        transcript.put("java", System.getProperty("java.version"));
        transcript.put("observables", options.observables());
        transcript.put("sources", sourceInventory(intel));

        List<Map<String, Object>> probeResults = new ArrayList<>();
        int producedEntities = 0;

        for (String raw : options.observables()) {
            Map<String, Object> probe = probeOne(intel, graph, raw);
            producedEntities += ((Number) probe.getOrDefault("newNodes", 0)).intValue();
            probeResults.add(probe);
        }
        transcript.put("probes", probeResults);

        intel.rescoreAll();
        transcript.put("graph", graphSummary(graph));
        transcript.put("persistence", persistenceRoundtrip(intel, investigation, graph));

        RiskScoringService risk = new RiskScoringService();
        CorrelationEngine correlation = new CorrelationEngine();
        AttackMatrixService attack = new AttackMatrixService();
        var report = correlation.run(graph);
        transcript.put("correlation", Map.of(
                "findings", report.findings().size(),
                "hypotheses", report.hypotheses().size()));

        ReportModel model = new ReportModel(investigation, graph, risk.caseSummary(graph),
                attack.actorProfiles(graph), attack.matrix(graph),
                report.findings(), report.hypotheses(), "Estoa 1.0", Instant.now());
        transcript.put("risk", Map.of(
                "average", round(graph.averageRisk()),
                "critical", model.risk().critical(),
                "high", model.risk().high(),
                "medium", model.risk().medium(),
                "low", model.risk().low()));

        Map<String, Object> reports = new LinkedHashMap<>();
        if (options.out() != null) {
            reports = generateReports(model, options.out());
            transcript.put("reports", reports);
        }

        transcript.put("finishedAt", Instant.now().toString());
        Path json = writeTranscript(transcript, options.out());

        printSummary(transcript, json);

        boolean persistenceOk = Boolean.TRUE.equals(((Map<?, ?>) transcript.get("persistence")).get("ok"));
        boolean reportsOk = options.out() == null || reports.values().stream()
                .allMatch(v -> Boolean.TRUE.equals(((Map<?, ?>) v).get("ok")));
        return producedEntities > 0 && persistenceOk && reportsOk ? 0 : 1;
    }

    // ---- ejecución de un observable ----

    private static Map<String, Object> probeOne(IntelService intel, OrionGraph graph, String raw) {
        GraphNode node = addObservable(intel, graph, raw);
        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("observable", raw);
        probe.put("key", node.getKey());
        probe.put("type", node.getEntity().getType().name());

        List<Transform> transforms = intel.transforms().forInput(node.getEntity().getType());
        boolean anyKey = !intel.sources().apiKeyManager().masked().isEmpty();


        List<Map<String, Object>> results = new ArrayList<>();
        int before = graph.nodeCount();
        for (Transform t : transforms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.id());
            item.put("name", t.name());
            if (t.requiresApiKey() && !anyKey) {
                item.put("ok", false);
                item.put("skipped", true);
                item.put("message", "Omitida: requiere clave de API y no hay ninguna configurada.");
                results.add(item);
                continue;
            }
            long start = System.currentTimeMillis();
            TransformResult r = intel.applyTransform(node.getKey(), t.id());
            item.put("ok", r.isOk());
            item.put("ms", System.currentTimeMillis() - start);
            item.put("message", r.getMessage());
            item.put("entities", r.getEntities().size());
            item.put("edges", r.getEdges().size());
            item.put("sample", summarizeEntities(r.getEntities(), 12));
            results.add(item);
        }
        probe.put("transforms", results);
        probe.put("newNodes", graph.nodeCount() - before);
        if (!anyKey) {
            probe.put("commercialSkipped", true);
        }
        return probe;
    }

    private static GraphNode addObservable(IntelService intel, OrionGraph graph, String raw) {
        int eq = raw.indexOf('=');
        if (eq > 0) {
            try {
                EntityType type = EntityType.valueOf(raw.substring(0, eq).trim().toUpperCase());
                return graph.addEntity(OrionEntity.of(type, raw.substring(eq + 1).trim()));
            } catch (IllegalArgumentException ignored) {
                // no es TIPO=valor: se detecta automáticamente
            }
        }
        return intel.addObservable(raw);
    }

    private static List<Map<String, Object>> summarizeEntities(List<OrionEntity> entities, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrionEntity e : entities) {
            if (out.size() >= limit) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getType().name());
            m.put("value", e.getValue());
            m.put("risk", round(e.getRiskScore()));
            m.put("sources", List.copyOf(e.getSources()));
            out.add(m);
        }
        return out;
    }

    // ---- persistencia, informes y evidencia ----

    private static Map<String, Object> persistenceRoundtrip(IntelService intel, Investigation inv, OrionGraph graph) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            intel.saveCase();
            List<Investigation> cases = intel.listCases();
            var loaded = intel.persistence().load(inv.getId());
            boolean present = cases.stream().anyMatch(c -> c.getId().equals(inv.getId()));
            boolean loadedOk = loaded.isPresent();
            int nodes = loadedOk ? loaded.get().graph().nodeCount() : -1;
            int edges = loadedOk ? loaded.get().graph().edgeCount() : -1;
            boolean ok = present && loadedOk && nodes == graph.nodeCount() && edges == graph.edgeCount();
            out.put("ok", ok);
            out.put("listed", present);
            out.put("loaded", loadedOk);
            out.put("nodes", nodes);
            out.put("edges", edges);
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
        }
        return out;
    }

    private static Map<String, Object> generateReports(ReportModel model, Path dir) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            out.put("error", Map.of("ok", false, "message", e.getMessage()));
            return out;
        }
        for (ReportType type : ReportType.values()) {
            for (ReportFormat format : ReportFormat.values()) {
                String name = "estoa-live-" + type.name().toLowerCase() + "." + format.extension();
                Map<String, Object> item = new LinkedHashMap<>();
                try {
                    Path target = dir.resolve(name);
                    new ReportGenerator().generate(type, format, model, target);
                    item.put("ok", true);
                    item.put("file", target.getFileName().toString());
                    item.put("bytes", Files.size(target));
                } catch (Exception e) {
                    item.put("ok", false);
                    item.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                out.put(type.name() + "/" + format.name(), item);
            }
        }
        return out;
    }

    private static Path writeTranscript(Map<String, Object> transcript, Path dir) {
        try {
            Path target = dir == null
                    ? Path.of("estoa-live-" + STAMP.format(Instant.now()) + ".json")
                    : dir.resolve("estoa-live-transcript.json");
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(), transcript);
            return target;
        } catch (Exception e) {
            System.err.println("No se pudo escribir la transcripción: " + e.getMessage());
            return null;
        }
    }

    private static void printSummary(Map<String, Object> transcript, Path json) {
        System.out.println();
        System.out.println("Estoa - verificación con datos reales");
        System.out.println("Java " + transcript.get("java") + " | inicio " + transcript.get("startedAt"));
        System.out.println("Conectores: " + transcript.get("sources"));
        System.out.println();

        List<?> probes = (List<?>) transcript.get("probes");
        for (Object o : probes) {
            Map<?, ?> p = (Map<?, ?>) o;
            System.out.println("[" + p.get("type") + "] " + p.get("observable") + "  -> " + p.get("newNodes") + " nodos nuevos");
            for (Object t : (List<?>) p.get("transforms")) {
                Map<?, ?> tr = (Map<?, ?>) t;
                String state = Boolean.TRUE.equals(tr.get("ok")) ? "OK " : (Boolean.TRUE.equals(tr.get("skipped")) ? "-- " : "FALLO");
                String ms = tr.get("ms") == null ? "" : tr.get("ms") + " ms";
                System.out.println("   " + state + " " + tr.get("id") + " (" + ms + "): " + tr.get("message"));
            }
            System.out.println();
        }

        Map<?, ?> graph = (Map<?, ?>) transcript.get("graph");
        System.out.println("Grafo: " + graph.get("nodes") + " nodos, " + graph.get("edges") + " aristas, riesgo medio " + graph.get("averageRisk"));
        Map<?, ?> persistence = (Map<?, ?>) transcript.get("persistence");
        System.out.println("Persistencia: " + (Boolean.TRUE.equals(persistence.get("ok")) ? "OK" : "FALLO")
                + " (" + persistence.get("nodes") + " nodos / " + persistence.get("edges") + " aristas)");
        System.out.println("Correlación: " + transcript.get("correlation"));
        if (transcript.containsKey("reports")) {
            Map<?, ?> reports = (Map<?, ?>) transcript.get("reports");
            long ok = reports.values().stream()
                    .filter(v -> v instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("ok"))).count();
            System.out.println("Informes: " + ok + "/" + reports.size());
        }
        if (json != null) System.out.println("Transcripción: " + json.toAbsolutePath());
        System.out.println();
    }

    // ---- utilidades ----

    private static List<Map<String, Object>> sourceInventory(IntelService intel) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SourceConnector c : intel.sources().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("category", c.category().name());
            m.put("requiresApiKey", c.requiresApiKey());
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> graphSummary(OrionGraph graph) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodes", graph.nodeCount());
        m.put("edges", graph.edgeCount());
        m.put("isolated", graph.getNodes().stream().filter(n -> graph.degree(n.getKey()) == 0).count());
        m.put("averageRisk", round(graph.averageRisk()));
        Map<String, Long> types = new LinkedHashMap<>();
        graph.typeCounts().forEach((k, v) -> types.put(k.name(), v));
        m.put("types", types);
        return m;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Options(List<String> observables, Path out) {
    }

    private static Options parse(String[] args) {
        List<String> observables = new ArrayList<>();
        Path out = null;
        boolean collect = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--live")) {
                collect = true;
            } else if (a.equals("--out") && i + 1 < args.length) {
                out = Path.of(args[++i]);
                collect = false;
            } else if (a.startsWith("--")) {
                collect = false;
            } else if (collect) {
                observables.add(a);
            }
        }
        return new Options(observables, out);
    }
}
