package com.orion.app.ui;

import com.orion.core.model.EntityType;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.HashMap;
import java.util.Map;

/**
 * Iconografia de Estoa basada en Tabler Icons (MIT, https://tabler.io/icons),
 * el mismo set que usa ThreatLens en su grafo. Cada icono es una lista de
 * trazos SVG de 24x24 que se dibuja como contorno, sin emojis ni fuentes
 * externas.
 */
public final class Icons {

    /** Lado del viewBox original de Tabler. */
    public static final double VIEWBOX = 24;

    private static final Map<String, String> PATHS = new HashMap<>();

    static {
        PATHS.put("world", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M3.6 9h16.8 M3.6 15h16.8 M11.5 3a17 17 0 0 0 0 18 M12.5 3a17 17 0 0 1 0 18");
        PATHS.put("map-pin", "M9 11a3 3 0 1 0 6 0a3 3 0 0 0 -6 0 M17.657 16.657l-4.243 4.243a2 2 0 0 1 -2.827 0l-4.244 -4.243a8 8 0 1 1 11.314 0z");
        PATHS.put("network", "M6 9a6 6 0 1 0 12 0a6 6 0 0 0 -12 0 M12 3c1.333 .333 2 2.333 2 6s-.667 5.667 -2 6 M12 3c-1.333 .333 -2 2.333 -2 6s.667 5.667 2 6 M6 9h12 M3 20h7 M14 20h7 M10 20a2 2 0 1 0 4 0a2 2 0 0 0 -4 0 M12 15v3");
        PATHS.put("box-multiple", "M7 3m0 2a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2z M17 17v2a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2h2");
        PATHS.put("mail", "M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10z M3 7l9 6l9 -6");
        PATHS.put("link", "M9 15l6 -6 M11 6l.463 -.536a5 5 0 0 1 7.071 7.072l-.534 .464 M13 18l-.397 .534a5.068 5.068 0 0 1 -7.127 0a4.972 4.972 0 0 1 0 -7.071l.524 -.463");
        PATHS.put("file", "M14 3v4a1 1 0 0 0 1 1h4 M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2z");
        PATHS.put("fingerprint", "M18.9 7a8 8 0 0 1 1.1 5v1a6 6 0 0 0 .8 3 M8 11a4 4 0 0 1 8 0v1a10 10 0 0 0 2 6 M12 11v2a14 14 0 0 0 2.5 8 M8 15a18 18 0 0 0 1.8 6 M4.9 19a22 22 0 0 1 -.9 -7v-1a8 8 0 0 1 12 -6.95");
        PATHS.put("shield-check", "M11.46 20.846a12 12 0 0 1 -7.96 -14.846a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 -.09 7.06 M15 19l2 2l4 -4");
        PATHS.put("bug", "M9 9v-1a3 3 0 0 1 6 0v1 M8 9h8a6 6 0 0 1 1 3v3a5 5 0 0 1 -10 0v-3a6 6 0 0 1 1 -3 M3 13l4 0 M17 13l4 0 M12 20l0 -6 M4 19l3.35 -2 M20 19l-3.35 -2 M4 7l3.75 2.4 M20 7l-3.75 2.4");
        PATHS.put("user-x", "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0 M6 21v-2a4 4 0 0 1 4 -4h3.5 M22 22l-5 -5 M17 22l5 -5");
        PATHS.put("users", "M9 7m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0 M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2 M16 3.13a4 4 0 0 1 0 7.75 M21 21v-2a4 4 0 0 0 -3 -3.85");
        PATHS.put("flag", "M5 5a5 5 0 0 1 7 0a5 5 0 0 0 7 0v9a5 5 0 0 1 -7 0a5 5 0 0 0 -7 0v-9z M5 21v-7");
        PATHS.put("alert-triangle", "M12 9v4 M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0z M12 16h.01");
        PATHS.put("bolt", "M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11");
        PATHS.put("crosshair", "M4 8v-2a2 2 0 0 1 2 -2h2 M4 16v2a2 2 0 0 0 2 2h2 M16 4h2a2 2 0 0 1 2 2v2 M16 20h2a2 2 0 0 0 2 -2v-2 M9 12l6 0 M12 9l0 6");
        PATHS.put("stack-2", "M12 4l-8 4l8 4l8 -4l-8 -4 M4 12l8 4l8 -4 M4 16l8 4l8 -4");
        PATHS.put("shield", "M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3");
        PATHS.put("building", "M3 21l18 0 M9 8l1 0 M9 12l1 0 M9 16l1 0 M14 8l1 0 M14 12l1 0 M14 16l1 0 M5 21v-16a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v16");
        PATHS.put("briefcase", "M3 7m0 2a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2z M8 7v-2a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2 M12 12l0 .01 M3 13a20 20 0 0 0 18 0");
        PATHS.put("user", "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0 M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2");
        PATHS.put("list", "M9 6l11 0 M9 12l11 0 M9 18l11 0 M5 6l0 .01 M5 12l0 .01 M5 18l0 .01");
        PATHS.put("plug", "M9.785 6l8.215 8.215l-2.054 2.054a5.81 5.81 0 1 1 -8.215 -8.215l2.054 -2.054z M4 20l3.5 -3.5 M15 4l-3.5 3.5 M20 9l-3.5 3.5");
        PATHS.put("share", "M6 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M18 6m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M18 18m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M8.7 10.7l6.6 -3.4 M8.7 13.3l6.6 3.4");
        PATHS.put("package", "M12 3l8 4.5l0 9l-8 4.5l-8 -4.5l0 -9l8 -4.5 M12 12l8 -4.5 M12 12l0 9 M12 12l-8 -4.5 M16 5.25l-8 4.5");
        PATHS.put("target", "M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M12 12m-5 0a5 5 0 1 0 10 0a5 5 0 1 0 -10 0 M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0");
        PATHS.put("plus", "M12 5l0 14 M5 12l14 0");
        PATHS.put("search", "M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M21 21l-6 -6");
        PATHS.put("zoom-in", "M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M7 10l6 0 M10 7l0 6 M21 21l-6 -6");
        PATHS.put("zoom-out", "M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M7 10l6 0 M21 21l-6 -6");
        PATHS.put("maximize", "M4 8v-2a2 2 0 0 1 2 -2h2 M4 16v2a2 2 0 0 0 2 2h2 M16 4h2a2 2 0 0 1 2 2v2 M16 20h2a2 2 0 0 0 2 -2v-2");
        PATHS.put("layout-grid", "M4 4m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z M14 4m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z M4 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z M14 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z");
        PATHS.put("file-text", "M14 3v4a1 1 0 0 0 1 1h4 M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2z M9 9l1 0 M9 13l6 0 M9 17l6 0");
        PATHS.put("git-branch", "M7 18m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M7 6m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M17 6m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M7 8l0 8 M9 18h6a2 2 0 0 0 2 -2v-5 M14 14l3 -3l3 3");
        PATHS.put("key", "M16.555 3.843l3.602 3.602a2.877 2.877 0 0 1 0 4.069l-2.643 2.643a2.877 2.877 0 0 1 -4.069 0l-.301 -.301l-6.558 6.558a2 2 0 0 1 -1.239 .578l-.175 .008h-1.172a1 1 0 0 1 -.993 -.883l-.007 -.117v-1.172a2 2 0 0 1 .467 -1.284l.119 -.13l.414 -.414h2v-2h2v-2l2.144 -2.144l-.301 -.301a2.877 2.877 0 0 1 0 -4.069l2.643 -2.643a2.877 2.877 0 0 1 4.069 0z M15 9h.01");
        PATHS.put("puzzle", "M4 7h3a1 1 0 0 0 1 -1v-1a2 2 0 0 1 4 0v1a1 1 0 0 0 1 1h3a1 1 0 0 1 1 1v3a1 1 0 0 0 1 1h1a2 2 0 0 1 0 4h-1a1 1 0 0 0 -1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-1a2 2 0 0 0 -4 0v1a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1h1a2 2 0 0 0 0 -4h-1a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1");
        PATHS.put("info-circle", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M12 9h.01 M11 12h1v4h1");
        PATHS.put("trash", "M4 7l16 0 M10 11l0 6 M14 11l0 6 M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12 M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3");
        PATHS.put("pencil", "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4 M13.5 6.5l4 4");
        PATHS.put("photo-down", "M15 8h.01 M12.5 21h-6.5a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v6.5 M3 16l5 -5c.928 -.893 2.072 -.893 3 0l4 4 M14 14l1 -1c.653 -.629 1.413 -.815 2.13 -.559 M19 16v6 M22 19l-3 3l-3 -3");
        PATHS.put("refresh", "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4 M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4");
        PATHS.put("check", "M5 12l5 5l10 -10");
        PATHS.put("x", "M18 6l-12 12 M6 6l12 12");
        PATHS.put("database", "M12 6m-8 0a8 3 0 1 0 16 0a8 3 0 1 0 -16 0 M4 6v6a8 3 0 0 0 16 0v-6 M4 12v6a8 3 0 0 0 16 0v-6");
        PATHS.put("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M12 7v5l3 3");
        PATHS.put("tag", "M7.5 7.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M3 6v5.172a2 2 0 0 0 .586 1.414l7.71 7.71a2.41 2.41 0 0 0 3.408 0l5.592 -5.592a2.41 2.41 0 0 0 0 -3.408l-7.71 -7.71a2 2 0 0 0 -1.414 -.586h-5.172a3 3 0 0 0 -3 3z");
        PATHS.put("dots", "M5 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M19 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0");
        PATHS.put("external-link", "M12 6h-6a2 2 0 0 0 -2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-6 M11 13l9 -9 M15 4h5v5");
        PATHS.put("arrow-left", "M5 12l14 0 M5 12l6 6 M5 12l6 -6");
        PATHS.put("chevron-down", "M6 9l6 6l6 -6");
        PATHS.put("eye", "M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0 M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6");
        PATHS.put("eye-off", "M10.585 10.587a2 2 0 0 0 2.829 2.828 M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87 M3 3l18 18");
    }

    private Icons() {
    }

    /** Resuelve alias usados por el modelo a nombres canonicos de Tabler. */
    private static String path(String name) {
        String key = switch (name == null ? "" : name) {
            case "globe" -> "world";
            case "boxes" -> "box-multiple";
            case "zap" -> "bolt";
            case "layers" -> "stack-2";
            case "share-2" -> "share";
            default -> name;
        };
        return PATHS.getOrDefault(key, PATHS.get("tag"));
    }

    /** Icono como nodo JavaFX listo para menus, botones y celdas. */
    public static Node of(String name, double size, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(path(name));
        p.setFill(null);
        p.setStroke(color == null ? Color.web("#c9d1d9") : color);
        p.setStrokeWidth(2);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double s = size / VIEWBOX;
        p.setScaleX(s);
        p.setScaleY(s);
        return new Group(p);
    }

    /** Icono del tipo de entidad con su color canonico. */
    public static Node entity(EntityType type, double size) {
        return of(type.icon(), size, Color.web(type.color()));
    }

    /** Icono neutro para acciones de la interfaz. */
    public static Node ui(String name, double size) {
        return of(name, size, Color.web("#8b949e"));
    }

    /** Dibuja un icono directamente sobre el canvas (sin nodos intermedios). */
    public static void draw(GraphicsContext g, String name, double x, double y, double size, Color color) {
        g.save();
        g.translate(x, y);
        double s = size / VIEWBOX;
        g.scale(s, s);
        g.setStroke(color);
        g.setLineWidth(2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.beginPath();
        g.appendSVGPath(path(name));
        g.stroke();
        g.restore();
    }

    /** Dibuja el icono del tipo centrado en (cx, cy). */
    public static void drawEntity(GraphicsContext g, EntityType type, double cx, double cy, double size, Color color) {
        draw(g, type.icon(), cx - size / 2, cy - size / 2, size, color);
    }
}
