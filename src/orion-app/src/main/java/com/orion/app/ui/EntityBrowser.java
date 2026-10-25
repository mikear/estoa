package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.RiskScore;
import com.orion.core.model.Tlp;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

abstract class EntityBrowser extends VBox {

    private final Consumer<GraphNode> onSelect;
    private final Consumer<String> onOpenInGraph;
    private final Consumer<GraphNode> onEnrich;
    private final Consumer<List<GraphNode>> onDelete;

    private OrionGraph graph = new OrionGraph();
    private final List<GraphNode> filtered = new ArrayList<>();
    private boolean suppressRefresh;

    protected final TextField filterField = new TextField();
    protected final ComboBox<EntityType> typeFilter = new ComboBox<>();
    protected final ComboBox<Tlp> tlpFilter = new ComboBox<>();
    protected final ComboBox<RiskScore.Level> riskFilter = new ComboBox<>();
    protected final Label countLabel = new Label();

    EntityBrowser(Consumer<GraphNode> onSelect, Consumer<String> onOpenInGraph,
                  Consumer<GraphNode> onEnrich, Consumer<List<GraphNode>> onDelete) {
        this.onSelect = onSelect;
        this.onOpenInGraph = onOpenInGraph;
        this.onEnrich = onEnrich;
        this.onDelete = onDelete;
        getStyleClass().add("entity-browser");
        setFillWidth(true);
    }

    protected final Node buildFilterBar() {
        filterField.setPromptText("Buscar valor, tipo, fuente o atributo...");
        filterField.setPrefWidth(230);

        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(EntityType.values());
        typeFilter.setConverter(new StringConverter<>() {
            @Override public String toString(EntityType value) {
                return value == null ? "Todos los tipos" : value.label();
            }
            @Override public EntityType fromString(String value) { return null; }
        });
        typeFilter.setValue(null);
        typeFilter.setPrefWidth(140);

        tlpFilter.getItems().add(null);
        tlpFilter.getItems().addAll(Tlp.values());
        tlpFilter.setConverter(new StringConverter<>() {
            @Override public String toString(Tlp value) {
                return value == null ? "Todos los TLP" : value.name();
            }
            @Override public Tlp fromString(String value) { return null; }
        });
        tlpFilter.setValue(null);
        tlpFilter.setPrefWidth(125);

        riskFilter.getItems().add(null);
        riskFilter.getItems().addAll(RiskScore.Level.values());
        riskFilter.setConverter(new StringConverter<>() {
            @Override public String toString(RiskScore.Level value) {
                return value == null ? "Todos los riesgos" : value.label();
            }
            @Override public RiskScore.Level fromString(String value) { return null; }
        });
        riskFilter.setValue(null);
        riskFilter.setPrefWidth(140);

        Button clear = new Button("Limpiar");
        clear.setGraphic(Icons.ui("x", 12));
        BooleanBinding noFilters = filterField.textProperty().isEmpty()
                .and(Bindings.createBooleanBinding(() -> typeFilter.getValue() == null, typeFilter.valueProperty()))
                .and(Bindings.createBooleanBinding(() -> tlpFilter.getValue() == null, tlpFilter.valueProperty()))
                .and(Bindings.createBooleanBinding(() -> riskFilter.getValue() == null, riskFilter.valueProperty()));
        clear.disableProperty().bind(noFilters);
        clear.setOnAction(e -> clearFilters());

        filterField.textProperty().addListener((o, a, b) -> refreshData());
        typeFilter.valueProperty().addListener((o, a, b) -> refreshData());
        tlpFilter.valueProperty().addListener((o, a, b) -> refreshData());
        riskFilter.valueProperty().addListener((o, a, b) -> refreshData());

        countLabel.getStyleClass().add("data-summary");
        countLabel.setMinWidth(100);

        HBox search = new HBox(5, icon("search"), filterField);
        search.setAlignment(Pos.CENTER_LEFT);

        FlowPane bar = new FlowPane(8, 6);
        bar.getStyleClass().add("data-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(search, typeFilter, tlpFilter, riskFilter, clear, countLabel);
        return bar;
    }

    private Label icon(String name) {
        Label label = new Label();
        label.setGraphic(Icons.ui(name, 13));
        return label;
    }

    private void clearFilters() {
        suppressRefresh = true;
        try {
            filterField.clear();
            typeFilter.setValue(null);
            tlpFilter.setValue(null);
            riskFilter.setValue(null);
        } finally {
            suppressRefresh = false;
        }
        refreshData();
    }

    public final void refresh(OrionGraph graph) {
        this.graph = graph == null ? new OrionGraph() : graph;
        refreshData();
    }

    public final void focusFilter() {
        filterField.requestFocus();
        filterField.selectAll();
    }

    final void applyFilterForCapture(String query, EntityType type) {
        filterField.setText(query == null ? "" : query);
        typeFilter.setValue(type);
    }

    protected final List<GraphNode> filteredNodes() {
        return List.copyOf(filtered);
    }

    protected abstract List<GraphNode> selectedNodes();

    protected final void deleteSelected() {
        List<GraphNode> nodes = selectedNodes();
        if (!nodes.isEmpty()) onDelete.accept(nodes);
    }

    protected final int totalNodes() {
        return graph.nodeCount();
    }

    protected final OrionGraph graph() {
        return graph;
    }

    protected final void refreshData() {
        if (suppressRefresh) return;
        filtered.clear();
        for (GraphNode node : graph.getNodes()) {
            if (EntityViewSupport.matches(node, filterField.getText(),
                    typeFilter.getValue(), tlpFilter.getValue(), riskFilter.getValue())) {
                filtered.add(node);
            }
        }
        int total = totalNodes();
        countLabel.setText(filtered.size() + " de " + total
                + (total == 1 ? " entidad" : " entidades"));
        rebuild();
    }

    protected abstract void rebuild();

    protected final void select(GraphNode node) {
        if (node != null && onSelect != null) onSelect.accept(node);
    }

    protected final void openInGraph(GraphNode node) {
        if (node != null && onOpenInGraph != null) onOpenInGraph.accept(node.getKey());
    }

    protected final ContextMenu contextMenu(GraphNode node) {
        MenuItem show = new MenuItem("Ver en el grafo");
        show.setGraphic(Icons.ui("share", 14));
        show.setOnAction(e -> openInGraph(node));

        MenuItem copy = new MenuItem("Copiar valor");
        copy.setGraphic(Icons.ui("file-text", 14));
        copy.setOnAction(e -> copy(node.getEntity().getValue()));

        MenuItem enrich = new MenuItem("Enriquecer");
        enrich.setGraphic(Icons.ui("bolt", 14));
        enrich.setOnAction(e -> onEnrich.accept(node));

        MenuItem delete = new MenuItem("Eliminar entidad");
        delete.setGraphic(Icons.ui("trash", 14));
        List<GraphNode> selected = selectedNodes();
        boolean inSelection = selected.stream().anyMatch(n -> n.getKey().equals(node.getKey()));
        List<GraphNode> targets = inSelection && selected.size() > 1 ? selected : List.of(node);
        if (targets.size() > 1) delete.setText("Eliminar " + targets.size() + " entidades");
        delete.setOnAction(e -> onDelete.accept(targets));

        return new ContextMenu(show, copy, new SeparatorMenuItem(), enrich, delete);
    }

    protected static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
