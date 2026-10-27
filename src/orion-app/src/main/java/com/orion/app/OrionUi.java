package com.orion.app;

import com.orion.app.ui.MainWindow;
import com.orion.core.model.Investigation;
import com.orion.intel.IntelService;
import com.orion.persistence.DataSourceFactory;
import com.orion.persistence.PersistenceService;
import com.orion.plugin.PluginManager;
import com.orion.sources.ApiKeyManager;
import com.orion.sources.SourceRegistry;
import com.orion.sources.SourcesBootstrap;
import com.orion.transform.TransformRegistry;
import com.orion.transform.TransformsBootstrap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

/**
 * Aplicacion JavaFX. El contexto Spring se crea en {@link OrionApp#main}
 * antes de lanzar el toolkit grafico.
 */
public class OrionUi extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(OrionUi.class);

    private IntelService intel;
    private List<PluginManager.LoadedPlugin> plugins = List.of();

    @Override
    public void init() {
        // demo y capturas usan una base en memoria: nunca escriben en los casos reales
        boolean efimera = getParameters().getUnnamed().stream()
                .anyMatch(p -> p.equals("--demo") || p.equals("--screenshot"));
        DataSource ds = efimera
                ? DataSourceFactory.h2Memory()
                : DataSourceFactory.fromEnvironment(
                        Path.of(System.getProperty("user.home"), ".orion", "orion.db"));
        PersistenceService persistence = new PersistenceService(ds);
        intel = new IntelService(persistence);

        // complementos del classpath + ~/.orion/plugins
        PluginManager plugins = new PluginManager(TransformRegistry.getInstance(),
                SourceRegistry.getInstance(), msg -> LOG.info("{}", msg));
        this.plugins = plugins.loadAll(
                Path.of(System.getProperty("user.home"), ".orion", "plugins"));
        LOG.info("Estoa iniciado: {} conectores, {} transformaciones, {} complementos",
                SourceRegistry.getInstance().size(), TransformRegistry.getInstance().size(), this.plugins.size());
    }

    @Override
    public void start(Stage stage) {
        List<String> params = getParameters().getUnnamed();
        boolean demo = params.contains("--demo");
        boolean noUpdateCheck = params.contains("--no-update-check");
        String shotDir = null;
        for (int i = 0; i < params.size() - 1; i++) {
            if (params.get(i).equals("--screenshot")) shotDir = params.get(i + 1);
        }

        MainWindow window = new MainWindow(stage, intel, plugins);
        if (demo) {
            window.loadDemo();
        }
        if (shotDir != null) {
            new ScreenshotMode(window, Path.of(shotDir)).captureAll(() -> {
                try {
                    OrionApp.context().close();
                } finally {
                    Platform.exit();
                    System.exit(0);
                }
            });
        } else if (!noUpdateCheck) {
            UpdateChecker.checkAndNotify(stage, false);
        }
    }

    @Override
    public void stop() {
        if (OrionApp.context() != null) OrionApp.context().close();
    }
}
