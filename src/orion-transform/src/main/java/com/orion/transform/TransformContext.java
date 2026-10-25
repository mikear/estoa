package com.orion.transform;

import com.orion.sources.ApiKeyManager;
import com.orion.sources.SourceRegistry;

/**
 * Contexto de ejecucion de una transformacion: acceso al registro de fuentes
 * y al almacen de claves. Las transformaciones nunca guardan estado propio.
 */
public class TransformContext {

    private final SourceRegistry sources;

    public TransformContext(SourceRegistry sources) {
        this.sources = sources == null ? SourceRegistry.getInstance() : sources;
    }

    public static TransformContext defaults() {
        return new TransformContext(SourceRegistry.getInstance());
    }

    public SourceRegistry sources() { return sources; }

    public ApiKeyManager keys() { return sources.apiKeyManager(); }

    public boolean hasKey(String service) {
        return sources.apiKeyManager().hasKey(service);
    }
}
