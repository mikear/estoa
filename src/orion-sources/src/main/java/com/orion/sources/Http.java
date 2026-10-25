package com.orion.sources;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP compartido por todos los conectores, con timeout, agente
 * identificativo y reintento unico ante errores transitorios.
 */
public final class Http {

    public static final String USER_AGENT = "Estoa-ThreatIntelligence/1.0 (investigation-platform)";
    /** Agente de navegador usado solo para inspeccionar sitios publicos. */
    public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Estoa/1.0";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {}

    public static String get(String url) throws IOException, InterruptedException {
        return get(url, Map.of());
    }

    public static String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .GET();
        headers.forEach(b::header);
        HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 500) {
            // reintento unico ante error de servidor
            Thread.sleep(700);
            resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return resp.body();

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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET();
        headers.forEach(b::header);
        HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(resp.statusCode(), resp.body(), lowerHeaders(resp));
    }

    public static Bytes bytes(String url, Map<String, String> headers, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET();
        headers.forEach(b::header);
        HttpResponse<byte[]> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Bytes(resp.statusCode(), resp.body(), lowerHeaders(resp));
    }

    private static Map<String, String> lowerHeaders(HttpResponse<?> resp) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        resp.headers().map().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) out.put(k.toLowerCase(), v.get(0));
        });
        return out;
    }

    public static String postJson(String url, String json) throws IOException, InterruptedException {
        return postJson(url, json, Map.of());
    }

    public static String postJson(String url, String json, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return resp.body();
    }

    public static String postForm(String url, String form) throws IOException, InterruptedException {
        return postForm(url, form, Map.of());
    }

    public static String postForm(String url, String form, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        headers.forEach(b::header);
        HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + " desde " + url);
        }
        return resp.body();
    }

    public static HttpClient client() { return CLIENT; }
}
