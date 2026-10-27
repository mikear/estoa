package com.orion.app.ui;

import com.orion.app.BuildInfo;
import com.orion.app.DemoDataService;
import com.orion.app.UpdateChecker;
import com.orion.correlation.CorrelationEngine;
import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.model.RiskScore;
import com.orion.core.model.Tlp;
import com.orion.core.util.NetUtil;
import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.graph.layout.LayoutType;
import com.orion.intel.AttackMatrixService;
import com.orion.intel.IntelService;
import com.orion.intel.RiskScoringService;
import com.orion.reports.ReportFormat;
import com.orion.reports.ReportGenerator;
import com.orion.reports.ReportModel;
import com.orion.reports.ReportType;
import com.orion.sources.ApiKeyManager;
import com.orion.sources.SourceConnector;
import com.orion.sources.SourceRegistry;
import com.orion.plugin.PluginManager;
import com.orion.transform.Transform;
import com.orion.transform.TransformResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Ventana principal: barra de menus (12), toolbar de analisis, panel
 * izquierdo (caso/entidades/IOC/fuentes), canvas central con HUD, panel
 * derecho (propiedades/riesgo/ATT&amp;CK) y barra de estado.
 */
public class MainWindow {

    /** A partir de este volumen el grafo se muestra en tabla para no saturar el lienzo. */
    private static final int LARGE_RESULT_THRESHOLD = 300;

    private final Stage stage;
    private final IntelService intel;
    private final java.util.List<PluginManager.LoadedPlugin> plugins;
    private final CorrelationEngine correlationEngine = new CorrelationEngine();
    private final AttackMatrixService attackService = new AttackMatrixService();
    private final RiskScoringService riskService = new RiskScoringService();

    private GraphCanvas canvas;
    private AttackMatrixView matrixView;
    private AdjacencyMatrixView adjacencyView;
    private TabPane centerTabs;
    private Tab graphTab, tableTab, treeTab, matrixTab, adjacencyTab;
    private EntityTableView tablePane;
    private EntityTreeView treePane;
    private Label statusLeft, statusCenter, statusRight, statusDb;
    private TextField searchField, addField;
    private ComboBox<LayoutType> layoutCombo;
    private RadioMenuItem groupByTypeItem;
    private final List<RadioMenuItem> groupAxisItems = new ArrayList<>();
    private final List<CheckMenuItem> tlpFilterItems = new ArrayList<>();
    private TabPane leftTabs, rightTabs;
    private ListView<GraphNode> entityList;
    private ListView<String> iocList, noteList;
    private ListView<SourceConnector> sourcesList;
    private VBox propertiesBox, riskBox, attackBox;
    private ScrollPane propertiesScroll, riskScroll, attackScroll;
    private Label caseNameLabel, caseMetaLabel;
    private final DateTimeFormatter clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Dialog<?> activeDialog;

    public MainWindow(Stage stage, IntelService intel) {
        this(stage, intel, java.util.List.of());
    }

    public MainWindow(Stage stage, IntelService intel, java.util.List<PluginManager.LoadedPlugin> plugins) {
        this.stage = stage;
        this.intel = intel;
        this.plugins = plugins == null ? java.util.List.of() : plugins;
        buildUi();
        stage.setOnCloseRequest(e -> {
            if (!saveOrWarn()) e.consume();
        });
    }

    private void buildUi() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("orion-root");
        root.setTop(buildTop());

        // Layout principal: paneles laterales redimensionables y colapsables
        SplitPane mainSplit = new SplitPane();
        mainSplit.getStyleClass().add("main-split");
        Node left = buildLeft();
        Node center = buildCenter();
        Node right = buildRight();
        mainSplit.getItems().addAll(left, center, right);
        mainSplit.setDividerPositions(0.20, 0.76);
        SplitPane.setResizableWithParent(left, Boolean.FALSE);
        SplitPane.setResizableWithParent(right, Boolean.FALSE);
        root.setCenter(mainSplit);
        root.setBottom(buildStatus());

        // tamano inicial adaptado a la pantalla disponible (85%, acotado)
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.max(1100, Math.min(1680, vb.getWidth() * 0.85));
        double h = Math.max(680, Math.min(1040, vb.getHeight() * 0.85));
        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(getClass().getResource("/orion.css").toExternalForm());
        stage.setTitle("Estoa - Plataforma de inteligencia de amenazas");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/orion-icon.png")));
        stage.setMinWidth(960);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();

        // modo compacto: reduce paddings/etiquetas en ventanas estrechas
        scene.widthProperty().addListener((o, a, b) -> {
            boolean compact = b.doubleValue() < 1360;
            if (compact && !root.getStyleClass().contains("compact")) {
                root.getStyleClass().add("compact");
            } else if (!compact) {
                root.getStyleClass().remove("compact");
            }
        });

        // el canvas observa el grafo del servicio desde el arranque
        canvas.setGraph(intel.getGraph());

        // atajos
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.F) {
                Tab selected = centerTabs == null ? null : centerTabs.getSelectionModel().getSelectedItem();
                if (selected == tableTab && tablePane != null) {
                    tablePane.focusFilter();
                } else if (selected == treeTab && treePane != null) {
                    treePane.focusFilter();
                } else {
                    searchField.requestFocus();
                }
            } else if (e.isControlDown() && e.getCode() == KeyCode.S) {
                saveCase();
            } else if (e.isControlDown() && e.getCode() == KeyCode.N) {
                newCase();
            } else if (e.isControlDown() && e.getCode() == KeyCode.O) {
                openCase();
            } else if (e.getCode() == KeyCode.F5) {
                intel.rescoreAll();
                refreshAll();
            } else if (e.getCode() == KeyCode.F11) {
                toggleFullScreen();
                e.consume();
            }
        });
    }

    // ================= TOP =================

    private VBox buildTop() {
        VBox box = new VBox(buildMenuBar(), buildToolbar());
        box.getStyleClass().add("top");
        return box;
    }

    private MenuBar buildMenuBar() {
        MenuBar mb = new MenuBar();
        mb.getStyleClass().add("orion-menubar");

        // Archivo
        Menu file = menu("Archivo", "file-text");
        MenuItem nuevo = item("Nuevo caso...", "plus", e -> newCase());
        MenuItem abrir = item("Abrir caso...", "file", e -> openCase());
        MenuItem guardar = item("Guardar caso", "check", e -> saveCase());
        MenuItem exportPng = item("Exportar grafo (PNG)...", "photo-down", e -> exportGraphPng());
        file.getItems().addAll(nuevo, abrir, guardar, new SeparatorMenuItem(), exportPng,
                new SeparatorMenuItem(), exitItem());

        // Edición
        Menu edit = menu("Edición", "pencil");
        MenuItem addObs = item("Añadir observable...", "plus", e -> addField.requestFocus());
        MenuItem delEntity = item("Eliminar selección", "trash", e -> deleteSelected());
        edit.getItems().addAll(addObs, delEntity);

        // Caso
        Menu caseMenu = menu("Caso", "briefcase");
        MenuItem meta = item("Metadatos del caso...", "pencil", e -> editCaseMetadata());
        MenuItem addNote = item("Añadir nota...", "plus", e -> addNote());
        caseMenu.getItems().addAll(meta, addNote);
        CheckMenuItem autoSave = new CheckMenuItem("Autoguardado al cerrar");
        autoSave.setSelected(true);
        caseMenu.getItems().add(autoSave);

        // Entidad
        Menu entity = menu("Entidad", "tag");
        Menu addTyped = menu("Añadir por tipo", "plus");
        for (EntityType t : EntityType.values()) {
            MenuItem mi = new MenuItem(t.label());
            mi.setGraphic(Icons.entity(t, 14));
            mi.setOnAction(e -> promptAddEntity(t));
            addTyped.getItems().add(mi);
        }
        entity.getItems().add(addTyped);

        // Transformación
        Menu transformMenu = menu("Transformación", "git-branch");
        MenuItem allTransforms = item("Ejecutar todas sobre la entidad seleccionada", "bolt",
                e -> runAllTransformsOnSelected());
        transformMenu.getItems().addAll(allTransforms, new SeparatorMenuItem());
        for (Transform.Category cat : Transform.Category.values()) {
            Menu sub = menu(cat.label(), "bolt");
            for (Transform t : intel.transforms().all()) {
                if (t.category() != cat) continue;
                sub.getItems().add(item(t.name(), "bolt", e -> runTransformOnSelected(t.id())));
            }
            if (!sub.getItems().isEmpty()) transformMenu.getItems().add(sub);
        }

        // Visualización
        Menu view = menu("Visualización", "layout-grid");
        for (LayoutType lt : LayoutType.values()) {
            view.getItems().add(item(lt.label(), "layout-grid", e -> applyLayout(lt)));
        }
        CheckMenuItem moveN1 = new CheckMenuItem("Arrastrar vecinos N1 con el nodo");
        moveN1.selectedProperty().addListener((o, a, b) -> canvas.setMoveNeighbors(b));

        Menu groupBy = menu("Agrupar por", "layers");
        ToggleGroup groupToggle = new ToggleGroup();
        groupByTypeItem = groupAxisItem("Tipo", GraphCanvas.GroupAxis.TYPE, groupToggle);
        RadioMenuItem groupRisk = groupAxisItem("Riesgo", GraphCanvas.GroupAxis.RISK, groupToggle);
        RadioMenuItem groupTlp = groupAxisItem("TLP", GraphCanvas.GroupAxis.TLP, groupToggle);
        RadioMenuItem groupCommunity = groupAxisItem("Comunidad", GraphCanvas.GroupAxis.COMMUNITY, groupToggle);
        groupBy.getItems().addAll(groupByTypeItem, groupRisk, groupTlp, groupCommunity);

        Menu filters = menu("Filtros del grafo", "search");
        Menu riskFilter = menu("Riesgo mínimo", "shield");
        ToggleGroup riskToggle = new ToggleGroup();
        RadioMenuItem riskAll = riskItem("Todas", 0, riskToggle);
        RadioMenuItem riskMedium = riskItem("Medio o superior (35+)", 35, riskToggle);
        RadioMenuItem riskHigh = riskItem("Alto o superior (60+)", 60, riskToggle);
        RadioMenuItem riskCritical = riskItem("Solo crítico (80+)", 80, riskToggle);
        riskAll.setSelected(true);
        riskFilter.getItems().addAll(riskAll, riskMedium, riskHigh, riskCritical);
        Menu tlpFilter = menu("TLP", "shield-check");
        for (Tlp tlp : Tlp.values()) {
            CheckMenuItem tlpItem = new CheckMenuItem("TLP:" + tlp.name());
            tlpItem.setSelected(true);
            tlpItem.selectedProperty().addListener((o, a, b) -> applyTlpFilter());
            tlpFilterItems.add(tlpItem);
            tlpFilter.getItems().add(tlpItem);
        }
        MenuItem clearFilters = item("Quitar filtros del grafo", "refresh", e -> {
            riskAll.setSelected(true);
            tlpFilterItems.forEach(i -> i.setSelected(true));
            canvas.setRiskFilter(0);
            canvas.setTlpFilter(List.of(Tlp.values()));
            canvas.fitView();
        });
        filters.getItems().addAll(riskFilter, tlpFilter, new SeparatorMenuItem(), clearFilters);

        Menu ego = menu("Foco de vecindad", "crosshair");
        MenuItem focusEgo = item("Enfocar entidad seleccionada (N1)", "crosshair", e -> {
            if (canvas.getSelectedKey() == null) {
                statusRight.setText("Selecciona una entidad para enfocar su vecindad");
            } else {
                canvas.focusNode(canvas.getSelectedKey());
            }
        });
        MenuItem egoBack = item("Volver al foco anterior", "maximize", e -> canvas.goBack());
        MenuItem egoExit = item("Salir del foco", "maximize", e -> canvas.exitEgoFocus());
        ego.getItems().addAll(focusEgo, egoBack, egoExit);

        CheckMenuItem legendItem = new CheckMenuItem("Mostrar leyenda");
        legendItem.setSelected(true);
        legendItem.selectedProperty().addListener((o, a, b) -> canvas.setLegendVisible(b));
        CheckMenuItem edgeLabelItem = new CheckMenuItem("Etiquetas de relaciones");
        edgeLabelItem.selectedProperty().addListener((o, a, b) -> canvas.setEdgeLabelsVisible(b));

        MenuItem showAll = item("Mostrar todas las entidades", "maximize", e -> {
            clearGroupingSelection();
            canvas.showAll();
            canvas.fitView();
        });
        MenuItem separate = item("Separar entidades solapadas", "layout-grid", e -> {
            canvas.separate();
            canvas.fitView();
        });
        MenuItem fit = item("Ajustar a pantalla", "maximize", e -> canvas.fitView());
        MenuItem full = item("Pantalla completa (F11)", "maximize", e -> toggleFullScreen());
        view.getItems().addAll(new SeparatorMenuItem(), moveN1, groupBy, filters, ego,
                legendItem, edgeLabelItem, new SeparatorMenuItem(), showAll, separate, fit, full);

        // Fuentes
        Menu sourcesMenu = menu("Fuentes", "database");
        MenuItem keys = item("Gestión de claves API...", "key", e -> openApiKeysDialog(true));
        sourcesMenu.getItems().add(keys);

        // Análisis
        Menu analysis = menu("Análisis", "search");
        MenuItem corr = item("Ejecutar correlación", "git-branch", e -> runCorrelation());
        MenuItem rescore = item("Recalcular riesgo", "refresh", e -> {
            intel.rescoreAll();
            refreshAll();
        });
        MenuItem matrix = item("Matriz ATT&CK", "crosshair", e -> showAttackMatrix());
        analysis.getItems().addAll(corr, rescore, new SeparatorMenuItem(), matrix);

        // Informes
        Menu reports = menu("Informes", "file-text");
        for (ReportType rt : ReportType.values()) {
            Menu sub = menu(rt.label(), "file-text");
            for (ReportFormat rf : ReportFormat.values()) {
                sub.getItems().add(item("Exportar " + rf.name(), "external-link",
                        e -> generateReport(rt, rf)));
            }
            reports.getItems().add(sub);
        }
        MenuItem dialog = item("Generador de informes...", "file-text", e -> openReportDialog(true));
        reports.getItems().addAll(new SeparatorMenuItem(), dialog);

        // Complementos
        Menu pluginsMenu = menu("Complementos", "puzzle");
        MenuItem pluginList = item("Complementos cargados", "list", e -> {
            StringBuilder sb = new StringBuilder();
            if (plugins.isEmpty()) {
                sb.append("No hay complementos instalados.\n\n");
            } else {
                for (PluginManager.LoadedPlugin p : plugins) {
                    sb.append(p.name()).append(" v").append(p.version())
                      .append(" [").append(p.origin()).append("]\n")
                      .append("   ").append(p.description()).append("\n\n");
                }
            }
            sb.append("Copia tus JAR en ~/.orion/plugins/ (interfaz com.orion.plugin.OrionPlugin).");
            showInfo("Complementos cargados (" + plugins.size() + ")", sb.toString());
        });
        pluginsMenu.getItems().add(pluginList);

        // Ventana
        Menu window = menu("Ventana", "layout-grid");
        MenuItem graph = item("Vista de grafo", "share", e -> showGraph());
        MenuItem table = item("Vista de tabla", "list", e -> showTable());
        MenuItem tree = item("Vista de árbol", "git-branch", e -> showTree());
        MenuItem matrixItem = item("Vista ATT&CK", "crosshair", e -> showAttackMatrix());
        MenuItem adjacencyItem = item("Vista matriz de adyacencia", "layout-grid", e -> showAdjacencyMatrix());
        window.getItems().addAll(graph, table, tree, matrixItem, adjacencyItem);

        // Ayuda
        Menu help = menu("Ayuda", "info-circle");
        MenuItem updates = item("Buscar actualizaciones...", "refresh",
                e -> UpdateChecker.checkAndNotify(stage, true));
        MenuItem about = item("Acerca de Estoa", "info-circle", e ->
                showInfo("Estoa - Plataforma de inteligencia de amenazas",
                        "Estoa v" + BuildInfo.version() + "\n\nPlataforma de inteligencia de amenazas, búsqueda de amenazas"
                                + " y análisis de enlaces.\n\n"
                                + intel.sources().size() + " conectores de datos\n"
                                + intel.transforms().size() + " transformaciones\n"
                                + "8 disposiciones de grafo - 7 tipos de informe\n"
                                + "Persistencia H2/PostgreSQL - Complementos SPI\n\n"
                                + "Riesgo heurístico de triaje: no escanea vulnerabilidades"
                                + " ni sustituye una evaluación cuantitativa.\n"
                                + "ATT&CK: subconjunto embebido offline ("
                                + attackService.bundleVersion() + ")\n\n"
                                + "Desarrollo: mikear\n"
                                + "https://github.com/mikear"));
        help.getItems().addAll(updates, new SeparatorMenuItem(), about);

        mb.getMenus().addAll(file, edit, caseMenu, entity, transformMenu, view,
                sourcesMenu, analysis, reports, pluginsMenu, window, help);
        return mb;
    }

    private Menu menu(String title, String icon) {
        Menu menu = new Menu(title);
        menu.setGraphic(Icons.ui(icon, 15));
        return menu;
    }

    private MenuItem item(String text, String icon,
                          javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem menuItem = new MenuItem(text);
        menuItem.setGraphic(Icons.ui(icon, 14));
        menuItem.setOnAction(action);
        return menuItem;
    }

    private RadioMenuItem groupAxisItem(String text, GraphCanvas.GroupAxis axis, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.selectedProperty().addListener((o, a, b) -> {
            if (b && canvas != null) {
                canvas.setGroupAxis(axis);
                canvas.setGrouped(true);
            }
        });
        groupAxisItems.add(item);
        return item;
    }

    private RadioMenuItem riskItem(String text, int minRisk, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.selectedProperty().addListener((o, a, b) -> {
            if (b && canvas != null) canvas.setRiskFilter(minRisk);
        });
        return item;
    }

    private void applyTlpFilter() {
        if (canvas == null) return;
        List<Tlp> enabled = new ArrayList<>();
        for (int i = 0; i < tlpFilterItems.size(); i++) {
            if (tlpFilterItems.get(i).isSelected()) enabled.add(Tlp.values()[i]);
        }
        canvas.setTlpFilter(enabled);
    }

    private void clearGroupingSelection() {
        boolean active = canvas != null && canvas.isGrouped();
        groupAxisItems.forEach(item -> item.setSelected(false));
        if (active) canvas.setGrouped(false);
    }

    private MenuItem exitItem() {
        MenuItem exit = new MenuItem("Salir");
        exit.setGraphic(Icons.ui("x", 14));
        exit.setOnAction(e -> {
            if (saveOrWarn()) stage.close();
        });
        return exit;
    }

    /** Guarda el caso; si falla, avisa y devuelve false para no cerrar la ventana. */
    private boolean saveOrWarn() {
        try {
            intel.saveCase();
            return true;
        } catch (RuntimeException ex) {
            showInfo("No se pudo guardar el caso",
                    ex.getMessage() + "\nLa ventana sigue abierta para no perder el trabajo.");
            return false;
        }
    }

    private ToolBar buildToolbar() {
        ToolBar tb = new ToolBar();
        tb.getStyleClass().add("orion-toolbar");

        Label logo = new Label("ESTOA");
        logo.getStyleClass().add("logo");

        addField = new TextField();
        addField.setPromptText("Añadir observable (dominio, IP, hash, URL, CVE, ASN...)");
        addField.setPrefWidth(300);
        addField.setOnAction(e -> {
            String v = addField.getText().trim();
            if (!v.isEmpty()) {
                intel.addObservable(v);
                addField.clear();
                canvas.relayout();
                refreshAll();
                canvas.fitView();
            }
        });
        Button addBtn = new Button("Añadir");
        addBtn.setGraphic(Icons.ui("plus", 14));
        addBtn.getStyleClass().add("primary");
        addBtn.setOnAction(e -> addField.fireEvent(new javafx.event.ActionEvent()));

        Label searchIcon = new Label();
        searchIcon.setGraphic(Icons.ui("search", 14));
        searchField = new TextField();
        searchField.setPromptText("Buscar en el grafo (Ctrl+F)");
        searchField.setPrefWidth(180);
        // teclear no debe repintar el lienzo en cada pulsacion
        javafx.animation.PauseTransition pausa = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        pausa.setOnFinished(ev -> highlightSearch(searchField.getText()));
        searchField.textProperty().addListener((o, a, b) -> pausa.playFromStart());

        layoutCombo = new ComboBox<>();
        layoutCombo.getItems().addAll(LayoutType.values());
        layoutCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LayoutType t) { return t == null ? "" : t.label(); }
            @Override public LayoutType fromString(String s) { return null; }
        });
        layoutCombo.setPrefWidth(178);
        layoutCombo.setValue(LayoutType.FORCE_DIRECTED);
        layoutCombo.setOnAction(e -> applyLayout(layoutCombo.getValue()));

        Label layoutLabel = new Label("Disposición:");
        layoutLabel.setGraphic(Icons.ui("layout-grid", 14));

        Label repulsionLabel = new Label("Repulsión");
        repulsionLabel.setTooltip(new Tooltip("Fuerza de repulsión entre entidades"));
        Slider repulsionSlider = new Slider(0.6, 2.5, 1.15);
        repulsionSlider.setPrefWidth(76);
        repulsionSlider.setTooltip(new Tooltip("Aumenta la separación entre entidades"));
        repulsionSlider.valueProperty().addListener((o, a, b) -> canvas.setRepulsion(b.doubleValue()));
        repulsionSlider.setOnMouseReleased(e -> {
            LayoutType lt = canvas.getCurrentLayout();
            if (lt == LayoutType.FORCE_DIRECTED || lt == LayoutType.ORGANIC) {
                canvas.relayout(lt);
                canvas.fitView();
            }
        });

        Button fitBtn = new Button("Ajustar");
        fitBtn.setGraphic(Icons.ui("maximize", 14));
        fitBtn.setTooltip(new Tooltip("Ajustar el grafo a la ventana"));
        fitBtn.setOnAction(e -> canvas.fitView());
        Button zoomIn = new Button();
        zoomIn.setGraphic(Icons.ui("zoom-in", 14));
        zoomIn.setTooltip(new Tooltip("Acercar"));
        zoomIn.setOnAction(e -> canvas.zoom(1.2));
        Button zoomOut = new Button();
        zoomOut.setGraphic(Icons.ui("zoom-out", 14));
        zoomOut.setTooltip(new Tooltip("Alejar"));
        zoomOut.setOnAction(e -> canvas.zoom(1 / 1.2));

        Button corrBtn = new Button("Correlación");
        corrBtn.setGraphic(Icons.ui("git-branch", 14));
        corrBtn.getStyleClass().add("primary");
        corrBtn.setOnAction(e -> runCorrelation());

        Button reportBtn = new Button("Informes");
        reportBtn.setGraphic(Icons.ui("file-text", 14));
        reportBtn.setOnAction(e -> openReportDialog(true));

        Separator s1 = new Separator(javafx.geometry.Orientation.VERTICAL);
        Separator s2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        tb.getItems().addAll(logo, s1, addField, addBtn, searchIcon, searchField,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                layoutLabel, layoutCombo, repulsionLabel, repulsionSlider, fitBtn, zoomIn, zoomOut,
                s2, corrBtn, reportBtn);
        return tb;
    }

    // ================= LEFT =================

    private Node buildLeft() {
        leftTabs = new TabPane();
        leftTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        leftTabs.getStyleClass().add("side-tabs");
        leftTabs.setPrefWidth(320);
        leftTabs.setMinWidth(220);
        leftTabs.setMaxWidth(Double.MAX_VALUE);

        // tab caso
        VBox caseBox = new VBox(8);
        caseBox.setPadding(new Insets(12));
        caseNameLabel = new Label();
        caseNameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        caseNameLabel.setWrapText(true);
        caseMetaLabel = new Label();
        caseMetaLabel.getStyleClass().add("muted");
        caseMetaLabel.setWrapText(true);
        Label notasTitle = sectionLabel("Notas del caso");
        noteList = new ListView<>();
        VBox.setVgrow(noteList, Priority.ALWAYS);
        caseBox.getChildren().addAll(caseNameLabel, caseMetaLabel,
                sectionLabel("Etiquetas"), tagsFlow(), notasTitle, noteList);
        Tab caseTab = new Tab("Caso", caseBox);
        caseTab.setGraphic(Icons.ui("briefcase", 14));

        // tab entidades
        VBox entityBox = new VBox(8);
        entityBox.setPadding(new Insets(12));
        entityList = new ListView<>();
        entityList.setCellFactory(v -> new GraphNodeCell());
        entityList.setOnMouseClicked(e -> {
            GraphNode n = entityList.getSelectionModel().getSelectedItem();
            if (n != null && e.getClickCount() >= 1) {
                canvas.select(n.getKey());
            }
        });
        VBox.setVgrow(entityList, Priority.ALWAYS);
        entityBox.getChildren().addAll(sectionLabel("Entidades del caso"), entityList);
        Tab entityTab = new Tab("Entidades", entityBox);
        entityTab.setGraphic(Icons.ui("network", 14));

        // tab IOC
        VBox iocBox = new VBox(8);
        iocBox.setPadding(new Insets(12));
        iocList = new ListView<>();
        iocList.setCellFactory(v -> new StringCell());
        VBox.setVgrow(iocList, Priority.ALWAYS);
        iocBox.getChildren().addAll(sectionLabel("Indicadores (riesgo >= 40)"), iocList);
        Tab iocTab = new Tab("IOC", iocBox);
        iocTab.setGraphic(Icons.ui("alert-triangle", 14));

        // tab fuentes
        VBox sourcesBox = new VBox(8);
        sourcesBox.setPadding(new Insets(12));
        sourcesList = new ListView<>();
        sourcesList.setCellFactory(v -> new SourceCell());
        VBox.setVgrow(sourcesList, Priority.ALWAYS);
        Button manageKeys = new Button("Gestión de claves API");
        manageKeys.setOnAction(e -> openApiKeysDialog(true));
        sourcesBox.getChildren().addAll(sectionLabel("Conectores"), sourcesList, manageKeys);
        Tab sourcesTab = new Tab("Fuentes", sourcesBox);
        sourcesTab.setGraphic(Icons.ui("database", 14));

        leftTabs.getTabs().addAll(caseTab, entityTab, iocTab, sourcesTab);
        return leftTabs;
    }

    private FlowPane tagsFlow() {
        FlowPane flow = new FlowPane(6, 6);
        for (String tag : intel.getCurrentCase().getTags()) {
            Label l = new Label(tag);
            l.getStyleClass().add("tag");
            flow.getChildren().add(l);
        }
        return flow;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("section");
        return l;
    }

    // ================= CENTER =================

    private Node buildCenter() {
        canvas = new GraphCanvas(1200, 800, this::onEntitySelected);
        canvas.setOnViewReset(this::clearGroupingSelection);
        canvas.setOnDeleteSelection(() -> removeEntities(canvas.getSelectedKeys()));
        matrixView = new AttackMatrixView();
        adjacencyView = new AdjacencyMatrixView();
        tablePane = new EntityTableView(this::onDataEntitySelected, this::showEntityInGraph,
                node -> runBestTransforms(node.getKey()), this::removeNodes);
        treePane = new EntityTreeView(this::onDataEntitySelected, this::showEntityInGraph,
                node -> runBestTransforms(node.getKey()), this::removeNodes);

        StackPane graphHolder = new StackPane(canvas);
        graphHolder.getStyleClass().add("canvas-pane");
        canvas.widthProperty().bind(graphHolder.widthProperty());
        canvas.heightProperty().bind(graphHolder.heightProperty());

        centerTabs = new TabPane();
        centerTabs.getStyleClass().add("main-tabs");
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        graphTab = new Tab("Grafo", graphHolder);
        graphTab.setGraphic(Icons.ui("share", 14));
        tableTab = new Tab("Tabla", tablePane);
        tableTab.setGraphic(Icons.ui("list", 14));
        treeTab = new Tab("Árbol", treePane);
        treeTab.setGraphic(Icons.ui("git-branch", 14));
        matrixTab = new Tab("ATT&CK", matrixView);
        matrixTab.setGraphic(Icons.ui("crosshair", 14));
        adjacencyTab = new Tab("Matriz", adjacencyView);
        adjacencyTab.setGraphic(Icons.ui("layout-grid", 14));
        centerTabs.getTabs().addAll(graphTab, tableTab, treeTab, matrixTab, adjacencyTab);

        canvas.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                GraphNode n = canvas.nodeAt(e.getX(), e.getY());
                if (n != null) showTransformMenu(n, e.getScreenX(), e.getScreenY());
            }
        });
        return centerTabs;
    }

    private void showTransformMenu(GraphNode node, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu();
        Label header = new Label("Transformaciones sobre " + node.getEntity().getLabel());
        header.getStyleClass().add("ctx-header");
        CustomMenuItem head = new CustomMenuItem(header);
        head.setHideOnClick(false);
        menu.getItems().add(head);
        MenuItem focus = new MenuItem("Enfocar vecindad N1");
        focus.setOnAction(e -> canvas.focusNode(node.getKey()));
        menu.getItems().addAll(focus, new SeparatorMenuItem());
        List<Transform> transforms = intel.transforms().forInput(node.getEntity().getType());
        for (Transform t : transforms) {
            MenuItem mi = new MenuItem((t.requiresApiKey() ? "[API] " : "") + t.name());
            mi.setOnAction(e -> runTransform(node.getKey(), t.id()));
            menu.getItems().add(mi);
        }
        if (transforms.isEmpty()) {
            MenuItem none = new MenuItem("(sin transformaciones para " + node.getEntity().getType().label() + ")");
            none.setDisable(true);
            menu.getItems().add(none);
        } else {
            MenuItem all = new MenuItem("Ejecutar todas las transformaciones");
            all.setOnAction(e -> runAllTransforms(node.getKey()));
            menu.getItems().add(all);
        }
        MenuItem delete = new MenuItem("Eliminar entidad");
        delete.setOnAction(e -> removeEntity(node.getKey()));
        menu.getItems().addAll(new SeparatorMenuItem(), delete);
        menu.show(canvas, screenX, screenY);
    }

    // ================= RIGHT =================

    private Node buildRight() {
        rightTabs = new TabPane();
        rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rightTabs.getStyleClass().add("side-tabs");
        rightTabs.setPrefWidth(360);
        rightTabs.setMinWidth(240);
        rightTabs.setMaxWidth(Double.MAX_VALUE);

        propertiesBox = new VBox(8);
        propertiesBox.setPadding(new Insets(12));
        propertiesScroll = new ScrollPane(propertiesBox);
        propertiesScroll.setFitToWidth(true);
        propertiesScroll.getStyleClass().add("panel-scroll");
        Tab props = new Tab("Propiedades", propertiesScroll);
        props.setGraphic(Icons.ui("list", 14));

        riskBox = new VBox(8);
        riskBox.setPadding(new Insets(12));
        riskScroll = new ScrollPane(riskBox);
        riskScroll.setFitToWidth(true);
        riskScroll.getStyleClass().add("panel-scroll");
        Tab risk = new Tab("Riesgo", riskScroll);
        risk.setGraphic(Icons.ui("shield", 14));

        attackBox = new VBox(8);
        attackBox.setPadding(new Insets(12));
        attackScroll = new ScrollPane(attackBox);
        attackScroll.setFitToWidth(true);
        attackScroll.getStyleClass().add("panel-scroll");
        Tab attack = new Tab("ATT&CK", attackScroll);
        attack.setGraphic(Icons.ui("crosshair", 14));

        rightTabs.getTabs().addAll(props, risk, attack);
        return rightTabs;
    }

    // ================= STATUS =================

    private HBox buildStatus() {
        HBox bar = new HBox(18);
        bar.getStyleClass().add("statusbar");
        bar.setPadding(new Insets(6, 14, 6, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        statusLeft = new Label("Sin caso");
        statusCenter = new Label("0 entidades - 0 relaciones");
        statusRight = new Label("Listo");
        statusDb = new Label("H2");
        Label clock = new Label();
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),
                        e -> clock.setText(LocalDateTime.now().format(clockFmt))));
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(statusLeft, statusCenter, spacer, statusRight, statusDb, clock);
        return bar;
    }

    // ================= ACCIONES =================

    public void loadDemo() {
        DemoDataService demo = new DemoDataService(intel);
        demo.load();
        canvas.setGraph(intel.getGraph());
        refreshAll();
        canvas.fitView();
    }

    private void onEntitySelected(GraphNode node) {
        rightTabs.getSelectionModel().select(0);
        renderProperties(node);
    }

    private void renderProperties(GraphNode node) {
        propertiesBox.getChildren().clear();
        if (node == null) {
            propertiesBox.getChildren().add(muted("Selecciona una entidad en el grafo."));
            return;
        }
        OrionEntity e = node.getEntity();
        propertiesBox.getChildren().addAll(
                kv("Tipo", e.getType().label()),
                kv("Valor", e.getValue()),
                kv("Riesgo", (int) e.getRiskScore() + "/100 (" + riskService.score(e, intel.getGraph()).getLevel().label() + ")"),
                kv("TLP", e.getTlp().name()),
                kv("Fuentes", e.getSources().isEmpty() ? e.getSource() : String.join(", ", e.getSources())));
        if (!e.getAttributes().isEmpty()) {
            propertiesBox.getChildren().add(sectionLabel("Atributos"));
            e.getAttributes().forEach((k, v) ->
                    propertiesBox.getChildren().add(kv(k, String.valueOf(v))));
        }
        List<String> refs = ReferenceCatalog.of(e);
        if (!refs.isEmpty()) {
            VBox refsBox = new VBox(6);
            for (String url : refs) {
                String refKey = referenceKey(url);
                boolean present = hasReference(node.getKey(), url);
                boolean visible = present && !canvas.isHidden(refKey);
                CheckBox cb = new CheckBox(shortUrl(url));
                cb.setTooltip(new Tooltip(url));
                cb.setSelected(present);
                cb.setOnAction(ev -> toggleReference(node, url, cb.isSelected()));

                ToggleButton eye = new ToggleButton();
                eye.setFocusTraversable(false);
                eye.setDisable(!present);
                eye.setSelected(visible);
                eye.setTooltip(new Tooltip(visible
                        ? "Ocultar esta entidad de referencia en el grafo"
                        : "Mostrar esta entidad de referencia en el grafo"));
                eye.setGraphic(eyeIcon(visible));
                eye.setOnAction(ev -> {
                    boolean show = eye.isSelected();
                    canvas.setHidden(refKey, !show);
                    eye.setGraphic(eyeIcon(show));
                    eye.setTooltip(new Tooltip(show
                            ? "Ocultar esta entidad de referencia en el grafo"
                            : "Mostrar esta entidad de referencia en el grafo"));
                    statusRight.setText(show
                            ? "Entidad de referencia visible"
                            : "Entidad de referencia oculta");
                });
                HBox.setHgrow(cb, Priority.ALWAYS);
                cb.setMaxWidth(Double.MAX_VALUE);
                HBox row = new HBox(6, cb, eye);
                row.setAlignment(Pos.CENTER_LEFT);
                refsBox.getChildren().add(row);
            }
            TitledPane pane = new TitledPane("Referencias (" + refs.size() + ")", refsBox);
            pane.setExpanded(true);
            propertiesBox.getChildren().add(pane);
        }
        RiskScore rs = riskService.score(e, intel.getGraph());
        propertiesBox.getChildren().add(sectionLabel("Factores de riesgo"));
        for (RiskScore.Factor f : rs.getFactors()) {
            propertiesBox.getChildren().add(kv(f.name(), "+" + Math.round(f.contribution()) + " - " + f.detail()));
        }
        // botones de accion
        HBox actions = new HBox(8);
        Button enrich = new Button("Enriquecer");
        enrich.setOnAction(ev -> runBestTransforms(node.getKey()));
        Button del = new Button("Eliminar");
        del.setOnAction(ev -> removeEntity(node.getKey()));
        actions.getChildren().addAll(enrich, del);
        propertiesBox.getChildren().add(actions);
    }

    private Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    private HBox kv(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("kv-k");
        k.setMinWidth(110);
        Label v = new Label(value);
        v.getStyleClass().add("kv-v");
        v.setWrapText(true);
        HBox row = new HBox(8, k, v);
        return row;
    }

    private String shortUrl(String url) {
        return url.length() <= 52 ? url : url.substring(0, 51) + "…";
    }

    private Node eyeIcon(boolean visible) {
        return Icons.of(visible ? "eye" : "eye-off", 13,
                Color.web(visible ? "#58a6ff" : "#8b949e"));
    }

    private String referenceKey(String url) {
        return "URL:" + url.toLowerCase();
    }

    private boolean hasReference(String hostKey, String url) {
        String refKey = referenceKey(url);
        return intel.getGraph().edgesOf(hostKey).stream()
                .anyMatch(edge -> "referencia".equals(edge.getLabel())
                        && edge.connects(hostKey, refKey));
    }

    private void toggleReference(GraphNode host, String url, boolean selected) {
        OrionGraph graph = intel.getGraph();
        String hostKey = host.getKey();
        String refKey = referenceKey(url);
        if (selected) {
            GraphNode ref = graph.node(refKey).orElse(null);
            if (ref == null) {
                OrionEntity entity = OrionEntity.of(EntityType.URL, url);
                entity.setSource("reference");
                entity.attr("referenceOf", hostKey);
                ref = graph.addEntity(entity);
                double angle = Math.toRadians(Math.floorMod(url.hashCode(), 360));
                ref.setX(host.getX() + Math.cos(angle) * 85);
                ref.setY(host.getY() + Math.sin(angle) * 85);
            }
            canvas.setHidden(refKey, false);
            graph.addEdge(host.getEntity(), ref.getEntity(), RelationshipType.RELATED_TO, "referencia");
        } else {
            canvas.setHidden(refKey, false);
            for (GraphEdge edge : List.copyOf(graph.edgesOf(hostKey))) {
                if ("referencia".equals(edge.getLabel()) && edge.connects(hostKey, refKey)) {
                    graph.removeEdge(edge);
                }
            }
            GraphNode ref = graph.node(refKey).orElse(null);
            if (ref != null && "reference".equals(ref.getEntity().getSource())
                    && graph.edgesOf(refKey).isEmpty()) {
                graph.removeNode(refKey);
            }
        }
        refreshAll();
        statusRight.setText(selected ? "Referencia añadida al grafo" : "Referencia retirada del grafo");
    }

    private void runBestTransforms(String key) {
        GraphNode node = intel.getGraph().node(key).orElse(null);
        if (node == null) return;
        List<String> ids = intel.transforms().forInput(node.getEntity().getType())
                .stream().filter(t -> !t.requiresApiKey()).map(Transform::id).toList();
        runTransforms(key, ids);
    }

    private void runAllTransformsOnSelected() {
        String key = canvas.getSelectedKey();
        if (key == null) {
            showInfo("Transformación", "Selecciona primero una entidad en el grafo.");
            return;
        }
        runAllTransforms(key);
    }

    private void runAllTransforms(String key) {
        GraphNode node = intel.getGraph().node(key).orElse(null);
        if (node == null) return;
        List<String> ids = intel.transforms().forInput(node.getEntity().getType())
                .stream().map(Transform::id).toList();
        if (ids.isEmpty()) {
            showInfo("Transformaciones", "No hay transformaciones para " + node.getEntity().getType().label() + ".");
            return;
        }
        runTransforms(key, ids);
    }

    private void runTransformOnSelected(String transformId) {
        String key = canvas.getSelectedKey();
        if (key == null) {
            showInfo("Transformación", "Selecciona primero una entidad en el grafo.");
            return;
        }
        runTransform(key, transformId);
    }

    private void runTransform(String key, String transformId) {
        runTransforms(key, List.of(transformId));
    }

    /**
     * Las transformaciones se ejecutan en segundo plano y se fusionan en el
     * grafo desde el hilo de la interfaz: el grafo tiene un solo escritor.
     */
    private void runTransforms(String key, List<String> transformIds) {
        GraphNode node = intel.getGraph().node(key).orElse(null);
        if (node == null || transformIds.isEmpty()) return;
        OrionEntity input = node.getEntity();
        OrionGraph caso = intel.getGraph();
        canvas.stopLiveLayout();
        statusRight.setText("Ejecutando " + transformIds.size() + " transformación(es)...");
        Thread worker = new Thread(() -> {
            List<TransformResult> results = new ArrayList<>();
            try {
                for (String id : transformIds) results.add(intel.fetchTransform(input, id));
            } catch (RuntimeException e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Platform.runLater(() -> statusRight.setText("Transformación fallida: " + msg));
                return;
            }
            Platform.runLater(() -> {
                if (intel.getGraph() != caso) {
                    statusRight.setText("Resultado descartado: el caso cambió durante la transformación");
                    return;
                }
                StringBuilder resumen = new StringBuilder();
                for (TransformResult r : results) {
                    intel.mergeTransform(key, r);
                    if (r.getMessage() != null && !r.getMessage().isBlank()) {
                        if (resumen.length() > 0) resumen.append(" · ");
                        resumen.append(r.getMessage());
                    }
                }
                refreshAll();
                relayoutOrTable(resumen.length() == 0 ? null : resumen.toString());
            });
        }, "orion-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void relayoutOrTable(String message) {
        if (intel.getGraph().nodeCount() > LARGE_RESULT_THRESHOLD) {
            showTable();
            statusRight.setText(message == null
                    ? "Resultado grande: vista de tabla (" + intel.getGraph().nodeCount() + " entidades)"
                    : message + " · vista de tabla");
        } else {
            canvas.relayout();
            if (message != null) statusRight.setText(message);
        }
    }

    private void removeEntity(String key) {
        if (key == null) return;
        removeEntities(List.of(key));
    }

    private void removeNodes(List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        removeEntities(nodes.stream().map(GraphNode::getKey).toList());
    }

    private void removeEntities(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        if (!confirmDelete(keys.size())) return;
        for (String key : List.copyOf(keys)) {
            intel.removeEntity(key);
        }
        intel.rescoreAll();
        canvas.select(null);
        refreshAll();
    }

    private boolean confirmDelete(int count) {
        ButtonType eliminar = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminan " + count + (count == 1 ? " entidad" : " entidades")
                        + " y sus relaciones del caso.", eliminar, ButtonType.CANCEL);
        alert.initOwner(stage);
        alert.setTitle("Eliminar del caso");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == eliminar).isPresent();
    }

    private void runAsync(String taskName, Runnable body) {
        runAsync(taskName, body, null);
    }

    private void runAsync(String taskName, Runnable body, Runnable onFxDone) {
        statusRight.setText(taskName + "...");
        Thread worker = new Thread(() -> {
            String error = null;
            try {
                body.run();
            } catch (RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String failure = error;
            Platform.runLater(() -> {
                refreshAll();
                statusRight.setText(failure == null ? "Listo" : taskName + " falló: " + failure);
                if (failure == null && onFxDone != null) onFxDone.run();
            });
        }, "orion-task");
        worker.setDaemon(true);
        worker.start();
    }

    public void runCorrelation() {
        var report = correlationEngine.run(intel.getGraph());
        renderRisk(report);
        rightTabs.getSelectionModel().select(1);
        refreshAll();
    }

    private void renderRisk(CorrelationEngine.CorrelationReport report) {
        riskBox.getChildren().clear();
        var caseRisk = riskService.caseSummary(intel.getGraph());
        riskBox.getChildren().addAll(
                sectionLabel("Riesgo del caso"),
                kv("Riesgo medio", (int) caseRisk.average() + "/100"),
                kv("Críticos", String.valueOf(caseRisk.critical())),
                kv("Altos", String.valueOf(caseRisk.high())),
                kv("Medios", String.valueOf(caseRisk.medium())),
                kv("Bajos", String.valueOf(caseRisk.low())));
        // distribucion
        HBox dist = new HBox(4);
        dist.setPadding(new Insets(4, 0, 8, 0));
        int total = Math.max(1, intel.getGraph().nodeCount());
        for (int v : caseRisk.distribution()) {
            Region bar = new Region();
            bar.setPrefHeight(10);
            bar.setPrefWidth(Math.max(2, v * 240.0 / total));
            bar.getStyleClass().add("bar");
            dist.getChildren().add(bar);
        }
        riskBox.getChildren().add(dist);
        riskBox.getChildren().add(muted("Riesgo heurístico: corroboración, amenaza (CVSS/EPSS/KEV),"
                + " conectividad y tipo. No es un escaneo ni una evaluación cuantitativa."));

        riskBox.getChildren().add(sectionLabel("Hallazgos (" + report.findings().size() + ")"));
        for (var f : report.findings()) {
            VBox item = new VBox(2);
            Label name = new Label("[" + f.ruleName() + "] confianza " + Math.round(f.confidence()) + "%");
            name.getStyleClass().add("finding");
            name.setWrapText(true);
            Label desc = new Label(f.description());
            desc.getStyleClass().add("muted");
            desc.setWrapText(true);
            item.getChildren().addAll(name, desc);
            riskBox.getChildren().add(item);
        }
        riskBox.getChildren().add(sectionLabel("Hipótesis (" + report.hypotheses().size() + ")"));
        for (var h : report.hypotheses()) {
            VBox item = new VBox(2);
            Label st = new Label(h.getStatus().label() + " - confianza " + Math.round(h.getConfidence()) + "%");
            st.getStyleClass().add(h.getStatus() == com.orion.correlation.Hypothesis.Status.CONFIRMED ? "ok" : "warn");
            Label s = new Label(h.getStatement());
            s.setWrapText(true);
            item.getChildren().addAll(st, s);
            riskBox.getChildren().add(item);
        }
        if (report.findings().isEmpty()) {
            riskBox.getChildren().add(muted("Sin hallazgos. Añade más entidades y enriquecelas."));
        }
    }

    private void renderAttackPanel() {
        attackBox.getChildren().clear();
        var rows = attackService.matrix(intel.getGraph());
        double coverage = attackService.coverage(intel.getGraph());
        int observed = rows.stream().mapToInt(r -> r.observedCount()).sum();
        attackBox.getChildren().addAll(
                sectionLabel("Cobertura ATT&CK"),
                kv("Técnicas observadas", observed + " de " + attackService.techniqueCount()
                        + " (" + Math.round(coverage) + "%)"),
                kv("Bundle", attackService.bundleVersion()));
        for (var actor : attackService.actorProfiles(intel.getGraph())) {
                attackBox.getChildren().add(kv(actor.name(), actor.techniques() + " técnicas - " + actor.origin()));
        }
        attackBox.getChildren().add(muted("Catálogo embebido offline; la cobertura no incluye"
                + " todas las técnicas de ATT&CK Enterprise."));
        attackBox.getChildren().add(muted("Vista completa en Análisis > Matriz ATT&CK."));
    }

    // ================= VISTAS =================

    public void showGraph() {
        if (centerTabs != null) centerTabs.getSelectionModel().select(graphTab);
        canvas.redraw();
    }

    public void showTable() {
        if (tablePane != null) tablePane.refresh(intel.getGraph());
        if (centerTabs != null) centerTabs.getSelectionModel().select(tableTab);
    }

    public void showTree() {
        if (treePane != null) treePane.refresh(intel.getGraph());
        if (centerTabs != null) centerTabs.getSelectionModel().select(treeTab);
    }

    public void filterTableForCapture(String query, EntityType type) {
        showTable();
        if (tablePane != null) tablePane.applyFilterForCapture(query, type);
    }

    public void filterTreeForCapture(String query, EntityType type) {
        showTree();
        if (treePane != null) treePane.applyFilterForCapture(query, type);
    }

    public void showAttackMatrix() {
        matrixView.update(attackService, intel.getGraph());
        if (centerTabs != null) centerTabs.getSelectionModel().select(matrixTab);
    }

    public void showAdjacencyMatrix() {
        adjacencyView.update(intel.getGraph());
        if (centerTabs != null) centerTabs.getSelectionModel().select(adjacencyTab);
    }

    private void onDataEntitySelected(GraphNode node) {
        if (node != null) canvas.select(node.getKey());
    }

    private void showEntityInGraph(String key) {
        showGraph();
        canvas.select(key);
        canvas.centerOn(key);
    }

    public void selectPrimaryEntity() {
        // selecciona la entidad de mayor riesgo del caso
        GraphNode best = null;
        for (GraphNode n : intel.getGraph().getNodes()) {
            if (best == null || n.getEntity().getRiskScore() > best.getEntity().getRiskScore()) best = n;
        }
        if (best != null) {
            canvas.select(best.getKey());
            onEntitySelected(best);
        }
    }

    // ================= DIÁLOGOS =================

    public void openApiKeysDialog(boolean modal) {
        ApiKeysDialog dialog = new ApiKeysDialog(stage, SourceRegistry.getInstance());
        if (modal) {
            dialog.showAndWait();
            refreshSources();
        } else {
            activeDialog = dialog;
            dialog.show();
        }
    }

    public void openReportDialog(boolean modal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Generador de informes");
        dialog.setHeaderText("Selecciona tipo y formato del informe");
        dialog.getDialogPane().getStyleClass().add("orion-dialog");
        ComboBox<ReportType> type = new ComboBox<>();
        type.getItems().addAll(ReportType.values());
        type.setValue(ReportType.TECHNICAL_INVESTIGATION);
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ReportType t) { return t == null ? "" : t.label(); }
            @Override public ReportType fromString(String s) { return null; }
        });
        ComboBox<ReportFormat> format = new ComboBox<>();
        format.getItems().addAll(ReportFormat.values());
        format.setValue(ReportFormat.PDF);
        Label desc = new Label(type.getValue().description());
        desc.setWrapText(true);
        desc.getStyleClass().add("muted");
        type.valueProperty().addListener((o, a, b) -> desc.setText(b.description()));
        VBox box = new VBox(10, new Label("Tipo de informe"), type, new Label("Formato"), format, desc);
        box.setPrefWidth(420);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (modal) {
            dialog.showAndWait().filter(b -> b == ButtonType.OK)
                    .ifPresent(b -> generateReport(type.getValue(), format.getValue()));
        } else {
            activeDialog = dialog;
            dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,
                    e -> {
                        e.consume();
                        generateReport(type.getValue(), format.getValue());
                    });
            dialog.show();
        }
    }

    public void closeFloatingDialogs() {
        if (activeDialog != null) {
            activeDialog.close();
            activeDialog = null;
        }
    }

    public void saveDemoReport(Path dir) {
        try {
            ReportGenerator gen = new ReportGenerator();
            gen.generate(ReportType.EXECUTIVE_SUMMARY, ReportFormat.PDF, buildReportModel(), dir.resolve("informe-demo.pdf"));
        } catch (Exception e) {
            statusRight.setText("Informe: " + e.getMessage());
        }
    }

    private void generateReport(ReportType type, ReportFormat format) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta de destino del informe");
        File f = chooser.showDialog(stage);
        if (f == null) return;
        // el modelo se construye en el hilo de la interfaz: el grafo no se comparte
        ReportModel model = buildReportModel();
        runAsync("Generando informe " + type.label() + " (" + format.name() + ")", () -> {
            try {
                new ReportGenerator().generate(type, format, model,
                        f.toPath().resolve(ReportGenerator.filename(type, format)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ReportModel buildReportModel() {
        OrionGraph g = intel.getGraph();
        var risk = riskService.caseSummary(g);
        var report = correlationEngine.run(g);
        return new ReportModel(intel.getCurrentCase(), g, risk,
                attackService.actorProfiles(g), attackService.matrix(g),
                report.findings(), report.hypotheses(),
                "Estoa 1.0", java.time.Instant.now());
    }

    private void exportGraphPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar grafo");
        chooser.setInitialFileName("estoa-grafo.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen PNG", "*.png"));
        File f = chooser.showSaveDialog(stage);
        if (f == null) return;
        capture(f.toPath());
        statusRight.setText("Grafo exportado a " + f.getName());
    }

    public void capture(Path target) {
        try {
            // si hay un dialogo flotante activo se captura su propia escena
            javafx.scene.Scene scene = stage.getScene();
            Dialog<?> dlg = activeDialog;
            if (dlg != null && dlg.getDialogPane() != null && dlg.getDialogPane().getScene() != null) {
                scene = dlg.getDialogPane().getScene();
            }
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            int[] buffer = new int[w * h];
            image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buffer, 0, w);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, buffer, 0, w);
            javax.imageio.ImageIO.write(img, "png", target.toFile());
        } catch (Exception e) {
            statusRight.setText("Captura fallida: " + e.getMessage());
        }
    }

    // ================= CASO =================

    private void newCase() {
        TextInputDialog dlg = new TextInputDialog("Nueva investigación");
        dlg.initOwner(stage);
        dlg.setTitle("Nuevo caso");
        dlg.setHeaderText("Crear un caso de investigación");
        dlg.setContentText("Nombre:");
        dlg.showAndWait().ifPresent(name -> {
            intel.setCurrentCase(new Investigation(name), new OrionGraph());
            canvas.setGraph(intel.getGraph());
            refreshAll();
            canvas.fitView();
        });
    }

    private void openCase() {
        List<Investigation> cases = intel.listCases();
        if (cases.isEmpty()) {
            showInfo("Abrir caso", "No hay casos guardados todavía.");
            return;
        }
        ChoiceDialog<Investigation> dlg = new ChoiceDialog<>(cases.get(0), cases);
        dlg.initOwner(stage);
        dlg.setTitle("Abrir caso");
        dlg.setHeaderText("Casos disponibles (" + cases.size() + ")");
        dlg.showAndWait().ifPresent(inv -> {
            if (intel.loadCase(inv.getId())) {
                canvas.setGraph(intel.getGraph());
                refreshAll();
            }
        });
    }

    private void saveCase() {
        intel.saveCase();
        statusRight.setText("Caso guardado (" + intel.getCurrentCase().getId() + ")");
        refreshAll();
    }

    private void editCaseMetadata() {
        Investigation inv = intel.getCurrentCase();
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(stage);
        dlg.setTitle("Metadatos del caso");
        TextField name = new TextField(inv.getName());
        TextArea desc = new TextArea(inv.getDescription());
        desc.setPrefRowCount(3);
        ComboBox<String> status = new ComboBox<>();
        for (com.orion.core.model.InvestigationStatus s : com.orion.core.model.InvestigationStatus.values()) {
            status.getItems().add(s.label());
        }
        status.setValue(inv.getStatus().label());
        ComboBox<String> priority = new ComboBox<>();
        for (com.orion.core.model.Priority p : com.orion.core.model.Priority.values()) {
            priority.getItems().add(p.label());
        }
        priority.setValue(inv.getPriority().label());
        TextField tags = new TextField(String.join(", ", inv.getTags()));
        VBox box = new VBox(10,
                new Label("Nombre"), name,
                new Label("Descripción"), desc,
                new Label("Estado"), status,
                new Label("Prioridad"), priority,
                new Label("Etiquetas (separadas por coma)"), tags);
        box.setPrefWidth(440);
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            inv.setName(name.getText());
            inv.setDescription(desc.getText());
            inv.setStatus(com.orion.core.model.InvestigationStatus.valueOfLabel(status.getValue()));
            inv.setPriority(com.orion.core.model.Priority.valueOfLabel(priority.getValue()));
            inv.getTags().clear();
            for (String t : tags.getText().split(",")) {
                if (!t.isBlank()) inv.addTag(t.trim());
            }
            refreshAll();
        });
    }

    private void addNote() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.initOwner(stage);
        dlg.setTitle("Añadir nota");
        dlg.setHeaderText("Nota del analista");
        dlg.showAndWait().ifPresent(n -> {
            intel.getCurrentCase().addNote(n);
            refreshAll();
        });
    }

    private void promptAddEntity(EntityType type) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.initOwner(stage);
        dlg.setTitle("Añadir entidad " + type.label());
        dlg.setContentText("Valor:");
        dlg.showAndWait().ifPresent(v -> {
            if (!v.isBlank()) {
                intel.getGraph().addEntity(OrionEntity.of(type, v.trim()));
                canvas.relayout();
                refreshAll();
            }
        });
    }

    private void deleteSelected() {
        Tab selected = centerTabs == null ? null : centerTabs.getSelectionModel().getSelectedItem();
        if (selected == tableTab && tablePane != null) {
            tablePane.deleteSelected();
        } else if (selected == treeTab && treePane != null) {
            treePane.deleteSelected();
        } else {
            removeEntities(canvas.getSelectedKeys());
        }
    }

    private void applyLayout(LayoutType type) {
        if (canvas != null && canvas.isGrouped()) clearGroupingSelection();
        layoutCombo.setValue(type);
        canvas.relayout(type);
        canvas.fitView();
        statusRight.setText("Disposición: " + type.label());
    }

    private void toggleFullScreen() {
        stage.setFullScreen(!stage.isFullScreen());
        statusRight.setText(stage.isFullScreen() ? "Pantalla completa (F11 para salir)" : "Listo");
    }

    public void applyLayoutForCapture(LayoutType type) { applyLayout(type); }

    public void showGroupedForCapture() {
        clearGroupingSelection();
        groupByTypeItem.setSelected(true);
        canvas.setGroupAxis(GraphCanvas.GroupAxis.TYPE);
        canvas.setGrouped(true);
    }

    public void focusGroupForCapture(EntityType type) { canvas.focusGroup(type); }

    public void showAllForCapture() {
        clearGroupingSelection();
        canvas.showAll();
        canvas.fitView();
    }

    public void addFirstReferenceForCapture() {
        String key = canvas.getSelectedKey();
        GraphNode node = key == null ? null : intel.getGraph().node(key).orElse(null);
        if (node == null) return;
        List<String> refs = ReferenceCatalog.of(node.getEntity());
        if (refs.isEmpty()) return;
        toggleReference(node, refs.get(0), true);
    }

    public void removeFirstReferenceForCapture() {
        String key = canvas.getSelectedKey();
        GraphNode node = key == null ? null : intel.getGraph().node(key).orElse(null);
        if (node == null) return;
        for (String url : ReferenceCatalog.of(node.getEntity())) {
            if (hasReference(node.getKey(), url)) {
                toggleReference(node, url, false);
                return;
            }
        }
    }

    public void clearSelectionForCapture() { canvas.select(null); }

    public void newCaseForCapture(String name) {
        intel.setCurrentCase(new Investigation(name), new OrionGraph());
        canvas.setGraph(intel.getGraph());
        refreshAll();
        canvas.fitView();
    }

    public void addObservableForCapture(String value) {
        intel.addObservable(value);
        canvas.relayout();
        refreshAll();
        canvas.fitView();
    }

    public void saveCaseForCapture() { saveCase(); }

    public void reopenCaseForCapture() {
        String id = intel.getCurrentCase().getId();
        if (intel.loadCase(id)) {
            canvas.setGraph(intel.getGraph());
            refreshAll();
            canvas.fitView();
        }
    }

    public void pressF11ForCapture() {
        var handler = stage.getScene().getOnKeyPressed();
        if (handler != null) {
            handler.handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F11,
                    false, false, false, false));
        }
    }

    private void highlightSearch(String query) {
        List<GraphNode> hits = intel.getGraph().search(query);
        entityList.getItems().setAll(hits);
        if (canvas != null) canvas.setSearchHighlight(query);
    }

    // ================= REFRESH =================

    public void refreshAll() {
        Investigation inv = intel.getCurrentCase();
        OrionGraph g = intel.getGraph();

        caseNameLabel.setText(inv.getName());
        caseMetaLabel.setText(inv.getId() + " - " + inv.getStatus().label() + " - " + inv.getPriority().label()
                + "\nAnalista: " + inv.getAnalyst());
        noteList.getItems().setAll(java.util.Collections.emptyList());
        noteList.getItems().addAll(inv.getNotes().stream().map(Investigation::displayNote).toList());

        entityList.getItems().setAll(List.copyOf(g.search(searchField.getText())));
        iocList.getItems().setAll(g.getNodes().stream()
                .filter(n -> n.getEntity().getRiskScore() >= 40)
                .sorted((a, b) -> Double.compare(b.getEntity().getRiskScore(), a.getEntity().getRiskScore()))
                .map(n -> String.format("%-42s %3d  %s", n.getEntity().getValue(),
                        (int) n.getEntity().getRiskScore(), n.getEntity().getType().label()))
                .toList());

        refreshSources();
        if (tablePane != null) tablePane.refresh(g);
        if (treePane != null) treePane.refresh(g);
        renderProperties(canvas.getSelectedKey() == null ? null :
                g.node(canvas.getSelectedKey()).orElse(null));
        if (riskBox.getChildren().isEmpty()) renderRisk(correlationEngine.run(g));
        renderAttackPanel();
        matrixView.update(attackService, g);
        if (adjacencyView != null) adjacencyView.update(g);

        statusLeft.setText(inv.getName() + " [" + inv.getId() + "]");
        statusCenter.setText(g.nodeCount() + " entidades - " + g.edgeCount() + " relaciones");
        statusDb.setText(intel.persistence().count() >= 0 ? "H2/PG" : "DB");
        canvas.redraw();
    }

    private void refreshSources() {
        sourcesList.getItems().setAll(intel.sources().all());
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ================= CELLS =================

    private class GraphNodeCell extends ListCell<GraphNode> {
        @Override
        protected void updateItem(GraphNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            setText(item.getEntity().getValue()
                    + "  (" + (int) item.getEntity().getRiskScore() + ")");
            setGraphic(Icons.entity(item.getEntity().getType(), 14));
            setTooltip(new Tooltip(item.getEntity().getType().label()));
        }
    }

    private static class StringCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item);
        }
    }

    private static class SourceCell extends ListCell<SourceConnector> {
        @Override
        protected void updateItem(SourceConnector item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean hasKey = !item.requiresApiKey()
                    || SourceRegistry.getInstance().apiKeyManager().hasKey(item.id());
            setGraphic(hasKey
                    ? Icons.of("check", 12, Color.web("#3fb950"))
                    : Icons.of("key", 12, Color.web("#d29921")));
            setText(hasKey ? item.name() : item.name() + "  (requiere clave)");
        }
    }
}
