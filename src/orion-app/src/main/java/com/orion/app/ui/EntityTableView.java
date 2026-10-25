package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.Tlp;
import com.orion.graph.GraphNode;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

final class EntityTableView extends EntityBrowser {

    private static final List<String> RISK_CLASSES =
            List.of("risk-critical", "risk-high", "risk-medium", "risk-low");

    private final TableView<GraphNode> table = new TableView<>();
    private boolean restoringSelection;

    EntityTableView(Consumer<GraphNode> onSelect, Consumer<String> onOpenInGraph,
                    Consumer<GraphNode> onEnrich, Consumer<List<GraphNode>> onDelete) {
        super(onSelect, onOpenInGraph, onEnrich, onDelete);
        getStyleClass().add("entity-table-view");

        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("Sin entidades para los filtros actuales"));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        buildColumns();
        configureInteractions();
        getChildren().addAll(buildFilterBar(), table);
    }

    private void buildColumns() {
        TableColumn<GraphNode, EntityType> type = new TableColumn<>("Tipo");
        type.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getEntity().getType()));
        type.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(EntityType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
                setGraphic(empty || item == null ? null : Icons.entity(item, 14));
            }
        });
        type.setComparator(Comparator.comparing(EntityType::label, String.CASE_INSENSITIVE_ORDER));
        type.setPrefWidth(145);
        type.setMinWidth(140);

        TableColumn<GraphNode, String> value = new TableColumn<>("Valor");
        value.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEntity().getValue()));
        value.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty ? null : new Tooltip(item));
            }
        });
        value.setComparator(String.CASE_INSENSITIVE_ORDER);
        value.setPrefWidth(260);

        TableColumn<GraphNode, Double> risk = new TableColumn<>("Riesgo");
        risk.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getEntity().getRiskScore()));
        risk.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(RISK_CLASSES);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(String.valueOf(Math.round(item)));
                setAlignment(Pos.CENTER_RIGHT);
                getStyleClass().add(EntityViewSupport.riskStyleClass(item));
                setTooltip(new Tooltip(EntityViewSupport.levelOf(item).label() + " (" + Math.round(item) + "/100)"));
            }
        });
        risk.setPrefWidth(80);

        TableColumn<GraphNode, Tlp> tlp = new TableColumn<>("TLP");
        tlp.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getEntity().getTlp()));
        tlp.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Tlp item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
                setTooltip(empty || item == null ? null : new Tooltip(item.description()));
            }
        });
        tlp.setComparator(Comparator.comparing(Tlp::name));
        tlp.setPrefWidth(75);

        TableColumn<GraphNode, String> sources = new TableColumn<>("Fuentes");
        sources.setCellValueFactory(cell -> new SimpleStringProperty(EntityViewSupport.sources(cell.getValue().getEntity())));
        sources.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null || item.isBlank() ? null : new Tooltip(item));
            }
        });
        sources.setComparator(String.CASE_INSENSITIVE_ORDER);
        sources.setPrefWidth(150);

        TableColumn<GraphNode, Integer> degree = new TableColumn<>("Enlaces");
        degree.setCellValueFactory(cell -> new SimpleObjectProperty<>(graph().degree(cell.getValue().getKey())));
        degree.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        degree.setPrefWidth(85);

        TableColumn<GraphNode, Instant> updated = new TableColumn<>("Visto");
        updated.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getEntity().getLastSeen()));
        updated.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Instant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : EntityViewSupport.formatShortTime(item));
                setTooltip(empty || item == null ? null : new Tooltip(EntityViewSupport.formatTime(item)));
            }
        });
        updated.setPrefWidth(85);
        updated.setMinWidth(75);

        table.getColumns().addAll(type, value, risk, tlp, sources, degree, updated);
        table.getSortOrder().add(risk);
        risk.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void configureInteractions() {
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!restoringSelection && b != null) select(b);
        });
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                GraphNode node = table.getSelectionModel().getSelectedItem();
                if (node != null) openInGraph(node);
            }
        });
        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                GraphNode node = table.getSelectionModel().getSelectedItem();
                if (node != null) copy(node.getEntity().getValue());
            } else if (event.getCode() == KeyCode.DELETE
                    && !(event.getTarget() instanceof TextInputControl)) {
                deleteSelected();
                event.consume();
            }
        });
        table.setRowFactory(view -> {
            TableRow<GraphNode> row = new TableRow<>() {
                @Override protected void updateItem(GraphNode item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll(RISK_CLASSES);
                    if (empty || item == null) {
                        return;
                    }
                    getStyleClass().add(EntityViewSupport.riskStyleClass(item.getEntity().getRiskScore()));
                }
            };
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) return;
                if (!table.getSelectionModel().getSelectedItems().contains(row.getItem())) {
                    table.getSelectionModel().clearAndSelect(row.getIndex());
                }
                contextMenu(row.getItem()).show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            return row;
        });
    }

    @Override
    protected List<GraphNode> selectedNodes() {
        return table.getSelectionModel().getSelectedItems().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    protected void rebuild() {
        List<String> selectedKeys = table.getSelectionModel().getSelectedItems().stream()
                .filter(java.util.Objects::nonNull)
                .map(GraphNode::getKey)
                .toList();
        restoringSelection = true;
        try {
            table.getItems().setAll(filteredNodes());
            table.sort();
            if (!selectedKeys.isEmpty()) {
                java.util.List<Integer> indexes = new java.util.ArrayList<>();
                for (int i = 0; i < table.getItems().size(); i++) {
                    if (selectedKeys.contains(table.getItems().get(i).getKey())) indexes.add(i);
                }
                if (!indexes.isEmpty()) {
                    int[] rest = indexes.stream().skip(1).mapToInt(Integer::intValue).toArray();
                    table.getSelectionModel().selectIndices(indexes.get(0), rest);
                }
            }
            table.refresh();
        } finally {
            restoringSelection = false;
        }
    }
}
