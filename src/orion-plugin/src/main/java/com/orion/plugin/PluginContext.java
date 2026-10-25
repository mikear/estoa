package com.orion.plugin;

import com.orion.sources.SourceConnector;
import com.orion.sources.SourceRegistry;
import com.orion.transform.Transform;
import com.orion.transform.TransformRegistry;

import java.util.function.Consumer;

/**
 * Superficie de integracion que se entrega al plugin en onLoad: registro de
 * transformaciones, conectores y canal de log.
 */
public class PluginContext {

    private final TransformRegistry transforms;
    private final SourceRegistry sources;
    private final Consumer<String> logger;

    public PluginContext(TransformRegistry transforms, SourceRegistry sources, Consumer<String> logger) {
        this.transforms = transforms;
        this.sources = sources;
        this.logger = logger == null ? m -> {} : logger;
    }

    public void registerTransform(Transform t) {
        transforms.register(t);
        log("transformación registrada: " + t.id() + " (" + t.name() + ")");
    }

    public void registerSource(SourceConnector c) {
        sources.register(c);
        log("conector registrado: " + c.id() + " (" + c.name() + ")");
    }

    public TransformRegistry transforms() { return transforms; }
    public SourceRegistry sources() { return sources; }

    public void log(String message) {
        logger.accept("[" + "] " + message);
    }
}
