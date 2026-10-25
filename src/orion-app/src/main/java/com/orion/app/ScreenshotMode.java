package com.orion.app;

import com.orion.app.ui.MainWindow;
import com.orion.core.model.EntityType;
import com.orion.graph.layout.LayoutType;

import java.nio.file.Path;

/**
 * Modo de captura automatica: recorre los estados principales de la interfaz
 * (workspace, seleccion, matriz ATT&amp;CK, correlacion, claves API, informes,
 * los 8 layouts, agrupacion, referencias y pantalla completa) y guarda un PNG
 * de cada uno antes de cerrar la aplicacion.
 */
public class ScreenshotMode {

    private final MainWindow window;
    private final Path dir;

    public ScreenshotMode(MainWindow window, Path dir) {
        this.window = window;
        this.dir = dir;
    }

    private static final String[] NAMES = {
            "01-workspace-grafo", "02-seleccion-propiedades", "03-matriz-attack",
            "04-correlacion-hipotesis", "05-claves-api", "06-generador-informes", "07-informe-generado",
            "08-layout-force-directed", "09-layout-organico", "10-layout-circular",
            "11-layout-rejilla", "12-layout-jerarquico", "13-layout-radial",
            "14-layout-clusteres", "15-layout-aleatorio", "16-vista-agrupada",
            "17-grupo-dominios", "18-referencias-grafo", "19-referencia-retirada",
            "20-pantalla-completa", "21-vista-tabla", "22-vista-arbol",
            "23-tabla-filtrada", "24-arbol-filtrado",
            "25-flujo-caso-nuevo", "26-flujo-observable", "27-flujo-correlacion",
            "28-flujo-caso-guardado", "29-flujo-caso-reabierto", "30-flujo-informe"
    };

    /** Programa la secuencia de capturas y llama a onDone al terminar. */
    public void captureAll(Runnable onDone) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear " + dir, e);
        }
        javafx.application.Platform.runLater(() -> step(1, onDone));
    }

    private void step(int n, Runnable onDone) {
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
        pause.setOnFinished(e -> {
            window.capture(dir.resolve(NAMES[n - 1] + ".png"));
            step(n + 1, onDone);
        });
        switch (n) {
            case 1 -> pause.play();
            case 2 -> { window.selectPrimaryEntity(); pause.play(); }
            case 3 -> { window.showAttackMatrix(); pause.play(); }
            case 4 -> { window.showGraph(); window.runCorrelation(); pause.play(); }
            case 5 -> { window.showGraph(); window.openApiKeysDialog(false); pause.play(); }
            case 6 -> { window.closeFloatingDialogs(); window.openReportDialog(false); pause.play(); }
            case 7 -> { window.closeFloatingDialogs(); window.saveDemoReport(dir); pause.play(); }
            case 8 -> {
                window.showGraph();
                window.clearSelectionForCapture();
                window.applyLayoutForCapture(LayoutType.FORCE_DIRECTED);
                pause.play();
            }
            case 9 -> { window.applyLayoutForCapture(LayoutType.ORGANIC); pause.play(); }
            case 10 -> { window.applyLayoutForCapture(LayoutType.CIRCULAR); pause.play(); }
            case 11 -> { window.applyLayoutForCapture(LayoutType.GRID); pause.play(); }
            case 12 -> { window.applyLayoutForCapture(LayoutType.HIERARCHICAL); pause.play(); }
            case 13 -> { window.applyLayoutForCapture(LayoutType.RADIAL); pause.play(); }
            case 14 -> { window.applyLayoutForCapture(LayoutType.CLUSTER); pause.play(); }
            case 15 -> { window.applyLayoutForCapture(LayoutType.RANDOM); pause.play(); }
            case 16 -> { window.showGroupedForCapture(); pause.play(); }
            case 17 -> { window.focusGroupForCapture(EntityType.DOMAIN); pause.play(); }
            case 18 -> {
                window.showAllForCapture();
                window.selectPrimaryEntity();
                window.addFirstReferenceForCapture();
                pause.play();
            }
            case 19 -> { window.removeFirstReferenceForCapture(); pause.play(); }
            case 20 -> { window.pressF11ForCapture(); pause.play(); }
            case 21 -> { window.pressF11ForCapture(); window.showTable(); pause.play(); }
            case 22 -> { window.showTree(); pause.play(); }
            case 23 -> { window.filterTableForCapture("cve", null); pause.play(); }
            case 24 -> { window.filterTreeForCapture(null, EntityType.DOMAIN); pause.play(); }
            case 25 -> { window.newCaseForCapture("Caso de validación QA"); pause.play(); }
            case 26 -> {
                window.addObservableForCapture("cdn-update-example.ru");
                window.addObservableForCapture("185.220.101.5");
                pause.play();
            }
            case 27 -> { window.runCorrelation(); pause.play(); }
            case 28 -> { window.saveCaseForCapture(); pause.play(); }
            case 29 -> { window.reopenCaseForCapture(); pause.play(); }
            case 30 -> { window.saveDemoReport(dir); pause.play(); }
            default -> onDone.run();
        }
    }
}
