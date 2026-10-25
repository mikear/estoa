package com.orion.app;

import java.io.InputStream;
import java.util.Properties;

/** Version de la aplicacion generada por Maven en version.properties. */
public final class BuildInfo {

    private static final String VERSION = load();

    private BuildInfo() {
    }

    public static String version() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/version.properties")) {
            if (in == null) return "dev";
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version", "dev").trim();
            return version.isEmpty() ? "dev" : version;
        } catch (Exception e) {
            return "dev";
        }
    }
}
