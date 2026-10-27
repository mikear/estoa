package com.orion.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP compartido por todos los conectores, con timeout, agente
 * identificativo y reintento unico ante errores transitorios. Las redirecciones
 * se siguen a mano para no acabar en un destino interno y el cuerpo se lee con
 * tope para que una respuesta enorme no agote la memoria.
 */
public final class Http {

    public static final String USER_AGENT = "Estoa-ThreatIntelligence/1.0 (investigation-platform)";
    /** Agente de navegador usado solo para inspeccionar sitios publicos. */
    public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Estoa/1.0";

    /** Tope de cuerpo: un JSON o HTML de fuente no llega a este tamano. */
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private Http() {}

    public static String get(String url) throws IOException, InterruptedException {
        return get(url, Map.of());
    }

    public static String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = request(url, Duration.ofSeconds(25), headers);
        HttpResponse<InputStream> resp = send(b);
        if (resp.statusCode() >= 500) {
            // reintento unico ante error de servidor
            resp.body().close();
            Thread.sleep(700);
            resp = send(b);
        }
        if (resp.statusCode() >= 400) {
            resp.body().close();
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return read(resp);
    }

    /** Respuesta HTTP con estado y cabeceras, sin lanzar excepcion ante 4xx/5xx. */
    public record Response(int status, String body, Map<String, String> headers) {
        public String header(String name) {
            return headers.getOrDefault(name == null ? "" : name.toLowerCase(), "");
        }
    }

    /** Respuesta binaria (favicons, imagenes) con estado y cabeceras. */
    public record Bytes(int status, byte[] body, Map<String, String> headers) {
        public String header(String name) {
            return headers.getOrDefault(name == null ? "" : name.toLowerCase(), "");
        }
    }

    public static Response fetch(String url, Map<String, String> headers, Duration timeout)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> resp = send(request(url, timeout, headers));
        return new Response(resp.statusCode(), read(resp), lowerHeaders(resp));
    }

    public static Bytes bytes(String url, Map<String, String> headers, Duration timeout)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> resp = send(request(url, timeout, headers));
        return new Bytes(resp.statusCode(), readBytes(resp), lowerHeaders(resp));
    }

    public static String postJson(String url, String json) throws IOException, InterruptedException {
        return postJson(url, json, Map.of());
    }

    public static String postJson(String url, String json, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = request(url, Duration.ofSeconds(25), headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<InputStream> resp = send(b);
        if (resp.statusCode() >= 400) {
            resp.body().close();
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return read(resp);
    }

    public static String postForm(String url, String form) throws IOException, InterruptedException {
        return postForm(url, form, Map.of());
    }

    public static String postForm(String url, String form, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = request(url, Duration.ofSeconds(25), headers)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        HttpResponse<InputStream> resp = send(b);
        if (resp.statusCode() >= 400) {
            resp.body().close();
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return read(resp);
    }

    private static HttpRequest.Builder request(String url, Duration timeout, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT);
        headers.forEach(b::header);
        return b;
    }

    /** Envia la peticion siguiendo redirecciones solo hacia hosts publicos. */
    private static HttpResponse<InputStream> send(HttpRequest.Builder builder)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> resp = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        for (int hops = 0; hops < MAX_REDIRECTS && resp.statusCode() >= 300 && resp.statusCode() < 400; hops++) {
            String location = resp.headers().firstValue("Location").orElse("");
            if (location.isBlank()) break; // sin destino: se devuelve la respuesta tal cual
            resp.body().close();
            URI next = builder.build().uri().resolve(location);
            if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                throw new IOException("Redirección a esquema no permitido: " + next);
            }
            if (isInternalHost(next.getHost())) {
                throw new IOException("Redirección a destino interno bloqueada: " + next.getHost());
            }
            // las cabeceras (p. ej. claves de API) solo viajan al mismo host
            HttpRequest prev = builder.build();
            HttpRequest.Builder nb = next.getHost().equalsIgnoreCase(prev.uri().getHost())
                    ? HttpRequest.newBuilder(prev, (n, v) -> true)
                    : HttpRequest.newBuilder().header("User-Agent", USER_AGENT);
            prev.timeout().ifPresent(nb::timeout);
            builder = nb.uri(next);
            resp = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        }
        return resp;
    }

    /** Loopback, redes privadas, link-local y multicast no son destinos validos. */
    static boolean isInternalHost(String host) {
        if (host == null || host.isBlank()) return true;
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                        || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // no se resuelve: la conexion fallara sola sin llegar a ningun destino interno
            return false;
        }
    }

    private static String read(HttpResponse<InputStream> resp) throws IOException {
        try (InputStream in = resp.body()) {
            return new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(HttpResponse<InputStream> resp) throws IOException {
        try (InputStream in = resp.body()) {
            return in.readNBytes(MAX_BODY);
        }
    }

    private static Map<String, String> lowerHeaders(HttpResponse<?> resp) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        resp.headers().map().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) out.put(k.toLowerCase(), v.get(0));
        });
        return out;
    }

    public static HttpClient client() { return CLIENT; }
}
