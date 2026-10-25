package com.orion.plugin;

/**
 * SPI de plugins de Estoa. Un plugin puede registrar transformaciones y
 * conectores propios al arrancar. Se declaran en
 * {@code META-INF/services/com.orion.plugin.OrionPlugin} dentro del JAR.
 */
public interface OrionPlugin {

    String id();

    String name();

    String version();

    default String author() { return "desconocido"; }

    default String description() { return ""; }

    /** Se invoca al cargar el plugin. */
    void onLoad(PluginContext context);

    /** Se invoca al descargar el plugin. */
    default void onUnload() {}
}
