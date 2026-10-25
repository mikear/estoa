package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class EntityTreeView extends EntityBrowser {

    private static final List<String> RISK_CLASSES =
            List.of("risk-critical", "risk-high", "risk-medium", "risk-low");
    private static final List<String> ROW_CLASSES =
            List.of("tree-group", "tree-entity", "tree-relation");

    private final TreeView<TreeEntry> tree = new TreeView<>();
    private final ComboBox<SortMode> sortCombo = new ComboBox<>();
    private final Label emptyLabel = new Label("Sin entidades para los filtros actuales");
    private final Set<EntityType> expandedGroups = EnumSet.allOf(EntityType.class);
    private final Set<String> expandedEntities = new HashSet<>();
    private boolean rebuilding;

    EntityTreeView(Consumer<GraphNode> onSelect, Consumer<String> onOpenInGraph,
                   Consumer<GraphNode> onEnrich, Consumer<List<GraphNode>> onDelete) {
        super(onSelect, onOpenInGraph, onEnrich, onDelete);
        getStyleClass().add("entity-tree-view");

        tree.getStyleClass().add("data-tree");
        tree.setShowRoot(false);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override protected void updateItem(TreeEntry item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(ROW_CLASSES);
                getStyleClass().removeAll(RISK_CLASSES);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    setContextMenu(null);
                    return;
                }
                setText(item.text());
                setGraphic(item.type() == null ? null : Icons.entity(item.type(), 14));
                setTooltip(new Tooltip(item.tooltip()));
                switch (item.kind()) {
                    case GROUP -> getStyleClass().add("tree-group");
                    case ENTITY -> {
                        getStyleClass().add("tree-entity");
                        getStyleClass().add(EntityViewSupport.riskStyleClass(item.node().getEntity().getRiskScore()));
                    }
                    case RELATION -> getStyleClass().add("tree-relation");
                }
                setContextMenu(null);
                if (item.kind() == TreeKind.GROUP) {
                    setOnContextMenuRequested(null);
                } else {
                    setOnContextMenuRequested(event -> {
                        TreeEntry current = getItem();
                        if (current == null || current.node() == null) return;
                        if (!tree.getSelectionModel().getSelectedItems().contains(getTreeItem())) {
                            tree.getSelectionModel().clearSelection();
                            tree.getSelectionModel().select(getTreeItem());
                        }
                        contextMenu(current.node()).show(this, event.getScreenX(), event.getScreenY());
                        event.consume();
                    });
                }
            }
        });

        sortCombo.getItems().addAll(SortMode.values());
        sortCombo.setValue(SortMode.RISK_DESC);
        sortCombo.setPrefWidth(190);
        sortCombo.valueProperty().addListener((o, a, b) -> refreshData());

        Button expandAll = new Button("Expandir todo");
        expandAll.setGraphic(Icons.ui("chevron-down", 13));
        expandAll.setOnAction(e -> {
            expandedGroups.addAll(EnumSet.allOf(EntityType.class));
            filteredNodes().forEach(node -> expandedEntities.add(node.getKey()));
            refreshData();
        });
        Button collapseAll = new Button("Contraer todo");
        collapseAll.setGraphic(Icons.ui("chevron-down", 13));
        collapseAll.setOnAction(e -> {
            expandedGroups.clear();
            expandedEntities.clear();
            refreshData();
        });

        HBox controls = new HBox(8, new Label("Orden"), sortCombo, expandAll, collapseAll);
        controls.getStyleClass().add("data-controls");
        controls.setAlignment(Pos.CENTER_LEFT);

        emptyLabel.getStyleClass().add("muted");
        emptyLabel.setMouseTransparent(true);
        StackPane holder = new StackPane(tree, emptyLabel);
        StackPane.setAlignment(emptyLabel, Pos.CENTER);
        VBox.setVgrow(holder, Priority.ALWAYS);

        tree.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!rebuilding && b != null && b.getValue() != null && b.getValue().node() != null) {
                select(b.getValue().node());
            }
        });
        tree.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) return;
            TreeItem<TreeEntry> item = tree.getSelectionModel().getSelectedItem();
            if (item == null || item.getValue() == null) return;
            if (item.getValue().kind() == TreeKind.GROUP) {
                item.setExpanded(!item.isExpanded());
            } else if (item.getValue().node() != null) {
                openInGraph(item.getValue().node());
            }
        });
        tree.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                TreeItem<TreeEntry> item = tree.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null && item.getValue().node() != null) {
                    copy(item.getValue().node().getEntity().getValue());
                }
            } else if (event.getCode() == KeyCode.DELETE
                    && !(event.getTarget() instanceof TextInputControl)) {
                deleteSelected();
                event.consume();
            }
        });

        getChildren().addAll(buildFilterBar(), controls, holder);
    }

    @Override
    protected List<GraphNode> selectedNodes() {
        List<GraphNode> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TreeItem<TreeEntry> item : tree.getSelectionModel().getSelectedItems()) {
            if (item == null || item.getValue() == null) continue;
            GraphNode node = item.getValue().node();
            if (node != null && seen.add(node.getKey())) nodes.add(node);
        }
        return nodes;
    }

    @Override
    protected void rebuild() {
        String selectedKey = null;
        TreeItem<TreeEntry> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null && selected.getValue().node() != null) {
            selectedKey = selected.getValue().node().getKey();
        }

        rebuilding = true;
        try {
            TreeItem<TreeEntry> root = new TreeItem<>(TreeEntry.root());
            root.setExpanded(true);

            Map<EntityType, List<GraphNode>> byType = new EnumMap<>(EntityType.class);
            for (GraphNode node : filteredNodes()) {
                byType.computeIfAbsent(node.getEntity().getType(), key -> new ArrayList<>()).add(node);
            }
            List<EntityType> types = new ArrayList<>(byType.keySet());
            types.sort(Comparator.comparing(EntityType::label, String.CASE_INSENSITIVE_ORDER));

            Comparator<GraphNode> nodeOrder = entityOrder();
            for (EntityType type : types) {
                List<GraphNode> nodes = byType.get(type);
                TreeItem<TreeEntry> group = new TreeItem<>(TreeEntry.group(type, nodes.size()));
                group.setExpanded(expandedGroups.contains(type));
                group.expandedProperty().addListener((o, a, b) -> {
                    if (b) expandedGroups.add(type); else expandedGroups.remove(type);
                });

                nodes.sort(nodeOrder);
                for (GraphNode node : nodes) {
                    TreeItem<TreeEntry> entity = new TreeItem<>(TreeEntry.entity(node, graph().degree(node.getKey())));
                    entity.setExpanded(expandedEntities.contains(node.getKey()));
                    entity.expandedProperty().addListener((o, a, b) -> {
                        if (b) expandedEntities.add(node.getKey()); else expandedEntities.remove(node.getKey());
                    });

                    List<GraphEdge> edges = new ArrayList<>(graph().edgesOf(node.getKey()));
                    edges.sort(Comparator.comparing(GraphEdge::getLabel, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(edge -> edge.other(node).getEntity().getValue(),
                                    String.CASE_INSENSITIVE_ORDER));
                    for (GraphEdge edge : edges) {
                        GraphNode other = edge.other(node);
                        boolean outgoing = edge.getSource() == node;
                        String arrow = outgoing ? "→" : "←";
                        String text = arrow + " " + edge.getLabel() + "  " + other.getEntity().getValue()
                                + "  [" + other.getEntity().getType().label() + "]";
                        entity.getChildren().add(new TreeItem<>(TreeEntry.relation(edge, other, text)));
                    }
                    group.getChildren().add(entity);
                }
                root.getChildren().add(group);
            }

            tree.setRoot(root);
            emptyLabel.setVisible(root.getChildren().isEmpty());
            if (selectedKey != null) {
                TreeItem<TreeEntry> found = findEntity(root, selectedKey);
                if (found != null) tree.getSelectionModel().select(found);
            }
        } finally {
            rebuilding = false;
        }
    }

    private TreeItem<TreeEntry> findEntity(TreeItem<TreeEntry> item, String key) {
        TreeEntry value = item.getValue();
        if (value != null && value.kind() == TreeKind.ENTITY && value.node().getKey().equals(key)) {
            return item;
        }
        for (TreeItem<TreeEntry> child : item.getChildren()) {
            TreeItem<TreeEntry> found = findEntity(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private Comparator<GraphNode> entityOrder() {
        SortMode mode = sortCombo.getValue() == null ? SortMode.RISK_DESC : sortCombo.getValue();
        Comparator<GraphNode> byValue = Comparator.comparing(
                node -> node.getEntity().getValue(), String.CASE_INSENSITIVE_ORDER);
        return switch (mode) {
            case RISK_DESC -> Comparator.comparingDouble(
                    (GraphNode node) -> node.getEntity().getRiskScore()).reversed().thenComparing(byValue);
            case RISK_ASC -> Comparator.comparingDouble(
                    (GraphNode node) -> node.getEntity().getRiskScore()).thenComparing(byValue);
            case VALUE_ASC -> byValue;
            case VALUE_DESC -> byValue.reversed();
            case DEGREE_DESC -> Comparator.comparingInt(
                    (GraphNode node) -> graph().degree(node.getKey())).reversed().thenComparing(byValue);
        };
    }

    private enum SortMode {
        RISK_DESC("Riesgo (mayor primero)"),
        RISK_ASC("Riesgo (menor primero)"),
        VALUE_ASC("Valor (A-Z)"),
        VALUE_DESC("Valor (Z-A)"),
        DEGREE_DESC("Relaciones (mayor primero)");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        @Override public String toString() {
            return label;
        }
    }

    private enum TreeKind {
        GROUP, ENTITY, RELATION
    }

    private record TreeEntry(TreeKind kind, EntityType type, GraphNode node,
                             GraphEdge edge, int count, String relationText) {

        static TreeEntry root() {
            return new TreeEntry(TreeKind.GROUP, null, null, null, 0, "");
        }

        static TreeEntry group(EntityType type, int count) {
            return new TreeEntry(TreeKind.GROUP, type, null, null, count, "");
        }

        static TreeEntry entity(GraphNode node, int degree) {
            return new TreeEntry(TreeKind.ENTITY, node.getEntity().getType(), node, null, degree, "");
        }

        static TreeEntry relation(GraphEdge edge, GraphNode node, String relationText) {
            return new TreeEntry(TreeKind.RELATION, node.getEntity().getType(), node, edge, 0, relationText);
        }

        String text() {
            return switch (kind) {
                case GROUP -> type == null ? "Entidades" : type.label() + " (" + count + ")";
                case ENTITY -> node.getEntity().getValue();
                case RELATION -> relationText;
            };
        }

        String tooltip() {
            return switch (kind) {
                case GROUP -> type == null ? "Entidades del caso"
                        : type.label() + " - " + count + " entidad(es)";
                case ENTITY -> node.getEntity().getType().label()
                        + "\nRiesgo: " + Math.round(node.getEntity().getRiskScore()) + "/100"
                        + "\nRelaciones: " + count;
                case RELATION -> "Relación: " + edge.getLabel()
                        + "\n" + node.getEntity().getType().label() + ": " + node.getEntity().getValue();
            };
        }
    }
}
