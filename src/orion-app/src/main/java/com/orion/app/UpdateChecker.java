package com.orion.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba la última release pública en GitHub y avisa sin descargar ni
 * ejecutar nada. La comprobación automática se cachea 24 h en ~/.orion.
 */
public final class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/mikear/estoa/releases/latest";
    private static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final Path CACHE =
            Path.of(System.getProperty("user.home"), ".orion", "update.properties");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private UpdateChecker() {
    }

    record Release(String version, String url) {
    }

    public static void checkAndNotify(Stage owner, boolean manual) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return findUpdate(manual);
            } catch (Exception e) {
                if (manual) Platform.runLater(() -> showError(owner, e));
                return Optional.<Release>empty();
            }
        }).thenAccept(update -> Platform.runLater(() -> {
            if (update.isPresent()) {
                Release release = update.get();
                markNotified(release.version());
                showUpdate(owner, release);
            } else if (manual) {
                showInfo(owner);
            }
        }));
    }

    static Optional<Release> findUpdate(boolean manual) throws IOException, InterruptedException {
        Properties cache = loadCache();
        long now = System.currentTimeMillis();
        if (!manual && now - longProp(cache, "lastCheck") < CHECK_INTERVAL_MS) {
            String version = cache.getProperty("latestVersion", "");
            String url = cache.getProperty("url", "");
            if (version.isBlank() || url.isBlank()) return Optional.empty();
            return isNewer(version) && !version.equals(cache.getProperty("notifiedVersion", ""))
                    ? Optional.of(new Release(version, url))
                    : Optional.empty();
        }

        Optional<Release> found = fetch(URI.create(API_URL));
        cache.setProperty("lastCheck", Long.toString(now));
        found.ifPresent(release -> {
            cache.setProperty("latestVersion", release.version());
            cache.setProperty("url", release.url());
        });
        saveCache(cache);
        return found.filter(release -> manual
                || !release.version().equals(cache.getProperty("notifiedVersion", "")));
    }

    static Optional<Release> fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Estoa/" + BuildInfo.version())
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondió HTTP " + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        if (root.path("draft").asBoolean(false) || root.path("prerelease").asBoolean(false)) {
            return Optional.empty();
        }
        String tag = root.path("tag_name").asText("").trim();
        String url = root.path("html_url").asText("").trim();
        if (tag.isBlank() || url.isBlank()) return Optional.empty();
        return Optional.of(new Release(normalize(tag), url));
    }

    static int compareVersions(String a, String b) {
        String[] left = normalize(a).split("\\.");
        String[] right = normalize(b).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? numeric(left[i]) : 0;
            int r = i < right.length ? numeric(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    static boolean isNewer(String version) {
        return compareVersions(version, BuildInfo.version()) > 0;
    }

    private static String normalize(String version) {
        String value = version == null ? "" : version.trim();
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    private static int numeric(String part) {
        Matcher matcher = Pattern.compile("^(\\d+)").matcher(part);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static Properties loadCache() {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(CACHE)) {
            properties.load(in);
        } catch (Exception ignored) {
            // primera ejecución o caché ilegible
        }
        return properties;
    }

    private static void saveCache(Properties properties) {
        try {
            Files.createDirectories(CACHE.getParent());
            try (var out = Files.newOutputStream(CACHE)) {
                properties.store(out, "Estoa update check");
            }
        } catch (Exception ignored) {
            // sin caché se reintenta en el siguiente arranque
        }
    }

    private static long longProp(Properties properties, String key) {
        try {
            return Long.parseLong(properties.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void markNotified(String version) {
        Properties properties = loadCache();
        properties.setProperty("notifiedVersion", version);
        saveCache(properties);
    }

    private static void showUpdate(Stage owner, Release release) {
        ButtonType download = new ButtonType("Descargar", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("Más tarde", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", download, later);
        alert.initOwner(owner);
        alert.setTitle("Actualización disponible");
        alert.setHeaderText("Estoa " + release.version() + " está disponible");
        alert.setContentText("Tienes la versión " + BuildInfo.version() + ".\n\n"
                + "Descarga la nueva versión desde la página de la release.");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == download) {
            open(release.url());
        }
    }

    private static void showInfo(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "No hay actualizaciones disponibles.",
                ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle("Estoa " + BuildInfo.version());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static void showError(Stage owner, Exception e) {
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "No se pudo comprobar si hay una versión nueva.\n" + e.getMessage(),
                ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle("Actualizaciones");
        alert.setHeaderText("Comprobación no disponible");
        alert.showAndWait();
    }

    private static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // se intenta con el comando del sistema
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ignored) {
            // sin navegador disponible
        }
    }
}
