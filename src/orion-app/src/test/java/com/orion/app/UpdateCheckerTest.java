package com.orion.app;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparaVersionesConPrefijoYLongitudDistinta() {
        assertTrue(UpdateChecker.compareVersions("v1.1.0", "1.0.0") > 0);
        assertTrue(UpdateChecker.compareVersions("1.0.10", "1.0.9") > 0);
        assertEquals(0, UpdateChecker.compareVersions("v1.0.0", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.0.1"));
        assertFalse(UpdateChecker.isNewer("1.0.0"));
    }

    @Test
    void leeReleasePublica() throws Exception {
        Optional<UpdateChecker.Release> release = fetch("""
                {"tag_name":"v1.1.0","html_url":"https://example.test/estoa/1.1.0",
                 "body":"Notas","draft":false,"prerelease":false}
                """);
        assertTrue(release.isPresent());
        assertEquals("1.1.0", release.get().version());
        assertEquals("https://example.test/estoa/1.1.0", release.get().url());
    }

    @Test
    void ignoraDraftYPrerelease() throws Exception {
        assertFalse(fetch("""
                {"tag_name":"v1.2.0","html_url":"https://example.test/draft","draft":true}
                """).isPresent());
        assertFalse(fetch("""
                {"tag_name":"v1.2.0","html_url":"https://example.test/pre","prerelease":true}
                """).isPresent());
    }

    @Test
    void versionCompiladaNoEsDev() {
        assertEquals("1.0.0", BuildInfo.version());
    }

    private static Optional<UpdateChecker.Release> fetch(String json) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/release", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/release");
            return UpdateChecker.fetch(uri);
        } finally {
            server.stop(0);
        }
    }
}
