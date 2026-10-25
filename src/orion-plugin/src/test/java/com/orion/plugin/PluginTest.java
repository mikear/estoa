package com.orion.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginTest {

    /** Plugin de prueba que registra una transformacion personalizada. */
    public static class EchoPlugin implements OrionPlugin {
        @Override public String id() { return "echo-plugin"; }
        @Override public String name() { return "Echo"; }
        @Override public String version() { return "1.0"; }
        @Override public void onLoad(PluginContext ctx) {
            ctx.log("eco de prueba");
        }
    }

    @Test
    void pluginContextLogsAndRegistries() {
        StringBuilder log = new StringBuilder();
        PluginContext ctx = new PluginContext(
                new com.orion.transform.TransformRegistry(),
                new com.orion.sources.SourceRegistry(),
                log::append);
        EchoPlugin p = new EchoPlugin();
        p.onLoad(ctx);
        assertTrue(log.toString().contains("eco de prueba"));
    }

    @Test
    void managerLoadsClasspathPlugins() {
        PluginManager pm = new PluginManager(
                new com.orion.transform.TransformRegistry(),
                new com.orion.sources.SourceRegistry(),
                m -> {});
        List<PluginManager.LoadedPlugin> plugins = pm.loadAll(null);
        assertNotNull(plugins);
        // sin declarar servicios, la lista queda vacia pero no falla
        assertTrue(plugins.isEmpty() || plugins.stream().allMatch(p -> p.id() != null));
    }
}
