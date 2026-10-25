package com.orion.sources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro global de conectores. Los modulos registran sus fuentes al
 * inicializarse y el motor consulta por operacion soportada.
 */
public class SourceRegistry {

    private final Map<String, SourceConnector> connectors = new LinkedHashMap<>();
    private ApiKeyManager apiKeyManager;

    private static final SourceRegistry INSTANCE = createInstance();

    private static SourceRegistry createInstance() { return new SourceRegistry(); }

    public static SourceRegistry getInstance() { return INSTANCE; }

    /** Registro adicional (p. ej. para pruebas o contextos aislados). */
    public SourceRegistry() {}

    public void register(SourceConnector c) {
        if (c != null) connectors.put(c.id(), c);
    }

    public void registerAll(Collection<? extends SourceConnector> cs) {
        cs.forEach(this::register);
    }

    public void setApiKeyManager(ApiKeyManager manager) { this.apiKeyManager = manager; }

    public ApiKeyManager apiKeyManager() {
        if (apiKeyManager == null) apiKeyManager = new ApiKeyManager();
        return apiKeyManager;
    }

    public Optional<SourceConnector> byId(String id) {
        return Optional.ofNullable(connectors.get(id));
    }

    public List<SourceConnector> all() { return new ArrayList<>(connectors.values()); }

    public List<SourceConnector> open() {
        return all().stream().filter(c -> c.category() == SourceConnector.Category.OPEN).toList();
    }

    public List<SourceConnector> commercial() {
        return all().stream().filter(c -> c.category() == SourceConnector.Category.COMMERCIAL).toList();
    }

    /** Conectores capaces de resolver una operacion. */
    public List<SourceConnector> forOperation(Operation op) {
        return all().stream().filter(c -> c.supports().contains(op)).toList();
    }

    public int size() { return connectors.size(); }
}
