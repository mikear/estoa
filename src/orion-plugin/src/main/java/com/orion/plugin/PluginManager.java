package com.orion.plugin;

import com.orion.sources.SourceRegistry;
import com.orion.transform.TransformRegistry;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de plugins: descubre implementaciones de {@link OrionPlugin} en el
 * classpath (ServiceLoader) y en el directorio de plugins externos
 * (~/.orion/plugins/*.jar), gestionando su ciclo de vida.
 */
public class PluginManager {

    private static final Logger LOG = Logger.getLogger(PluginManager.class.getName());

    /** Plugin cargado con su estado. */
    public record LoadedPlugin(OrionPlugin plugin, String origin) {
        public String id() { return plugin.id(); }
        public String name() { return plugin.name(); }
        public String version() { return plugin.version(); }
        public String author() { return plugin.author(); }
        public String description() { return plugin.description(); }
    }

    private final List<LoadedPlugin> loaded = new ArrayList<>();
    private final TransformRegistry transforms;
    private final SourceRegistry sources;
    private final Consumer<String> logger;

    public PluginManager(TransformRegistry transforms, SourceRegistry sources) {
        this(transforms, sources, null);
    }

    public PluginManager(TransformRegistry transforms, SourceRegistry sources, Consumer<String> logger) {
        this.transforms = transforms;
        this.sources = sources;
        this.logger = logger == null ? m -> LOG.log(Level.INFO, m) : logger;
    }

    /** Carga plugins del classpath y del directorio externo. */
    public synchronized List<LoadedPlugin> loadAll(Path pluginDir) {
        loaded.clear();
        PluginContext ctx = new PluginContext(transforms, sources, logger);
        try {
            ServiceLoader<OrionPlugin> loader = ServiceLoader.load(OrionPlugin.class);
            for (OrionPlugin p : loader) {
                safeLoad(ctx, p, "classpath");
            }
        } catch (Exception e) {
            logger.accept("error escaneando classpath: " + e.getMessage());
        }
        if (pluginDir != null && pluginDir.toFile().isDirectory()) {
            File[] jars = pluginDir.toFile().listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    try {
                        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                                getClass().getClassLoader());
                        ServiceLoader<OrionPlugin> loader = ServiceLoader.load(OrionPlugin.class, cl);
                        for (OrionPlugin p : loader) {
                            safeLoad(ctx, p, jar.getName());
                        }
                    } catch (Exception e) {
                        logger.accept("error cargando " + jar.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        return List.copyOf(loaded);
    }

    private void safeLoad(PluginContext ctx, OrionPlugin p, String origin) {
        try {
            p.onLoad(ctx);
            loaded.add(new LoadedPlugin(p, origin));
            logger.accept("complemento cargado: " + p.name() + " v" + p.version() + " [" + origin + "]");
        } catch (Exception e) {
            logger.accept("fallo al cargar el complemento " + p.id() + ": " + e.getMessage());
        }
    }

    public List<LoadedPlugin> getLoaded() { return List.copyOf(loaded); }

    public int count() { return loaded.size(); }
}
