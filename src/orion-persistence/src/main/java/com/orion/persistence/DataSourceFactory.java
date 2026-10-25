package com.orion.persistence;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Fabrica de datasources. Perfil por defecto: H2 embebida en ~/.orion/orion.db.
 * Perfil PostgreSQL: variables ORION_DB_URL / ORION_DB_USER / ORION_DB_PASS
 * o construccion programatica para despliegues multi-analista.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static DataSource h2Embedded(Path file) {
        try {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:" + file.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE");
            ds.setUser("orion");
            ds.setPassword("");
            return ds;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el origen de datos H2", e);
        }
    }

    public static DataSource h2Memory() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:orion_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("orion");
        ds.setPassword("");
        return ds;
    }

    public static DataSource postgres(String url, String user, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }

    /** Perfil segun entorno: ORION_DB_URL jdbc:postgresql://... o H2 embebida. */
    public static DataSource fromEnvironment(Path h2DefaultFile) {
        String url = System.getenv("ORION_DB_URL");
        if (url != null && url.startsWith("jdbc:postgresql")) {
            return postgres(url,
                    System.getenv().getOrDefault("ORION_DB_USER", "orion"),
                    System.getenv().getOrDefault("ORION_DB_PASS", ""));
        }
        return h2Embedded(h2DefaultFile);
    }

    /** Comprueba la conexión devolviendo el nombre del producto. */
    public static String probe(DataSource ds) {
        try (var c = ds.getConnection()) {
            return c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            return "inaccesible: " + e.getMessage();
        }
    }

    static Properties connectionProperties() {
        Properties p = new Properties();
        p.putAll(Map.of("applicationName", "Estoa - Inteligencia de amenazas"));
        return p;
    }
}
