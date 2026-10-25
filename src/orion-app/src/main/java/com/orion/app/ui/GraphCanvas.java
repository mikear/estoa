package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.RiskScore;
import com.orion.core.model.Tlp;
import com.orion.graph.CommunityDetector;
import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.graph.layout.CollisionResolver;
import com.orion.graph.layout.ForceDirectedLayout;
import com.orion.graph.layout.LayoutType;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class GraphCanvas extends Canvas {

    public enum GroupAxis {
        TYPE("Tipo"),
        RISK("Riesgo"),
        TLP("TLP"),
        COMMUNITY("Comunidad");

        private final String label;

        GroupAxis(String label) { this.label = label; }

        public String label() { return label; }
    }

    private static final Color BG = Color.web("#0d1117");
    private static final Color PANEL = Color.web("#161b22", 0.94);
    private static final Color BORDER = Color.web("#30363d");
    private static final Color TEXT = Color.web("#c9d1d9");
    private static final Color MUTED = Color.web("#8b949e");
    private static final Color ACCENT = Color.web("#58a6ff");
    private static final Color CRITICAL = Color.web("#f85149");

    private OrionGraph graph = new OrionGraph();
    private double scale = 1;
    private double panX = 0, panY = 0;
    private String selectedKey;
    private final Set<String> selectedKeys = new HashSet<>();
    private final Set<String> hiddenKeys = new HashSet<>();
    private String hoverKey;
    private GraphNode dragNode;
    private List<GraphNode> dragNeighbors = List.of();
    private boolean moveNeighbors;
    private double lastX, lastY;
    private double pressX, pressY;
    private boolean dragging;
    private boolean panning;
    private boolean bandSelecting;
    private double bandStartX, bandStartY, bandEndX, bandEndY;

    private ForceDirectedLayout liveForce;
    private LayoutType currentLayout = LayoutType.FORCE_DIRECTED;
    private final AnimationTimer animator;
    private final Consumer<GraphNode> onSelect;
    private Runnable onViewReset;
    private Runnable onDeleteSelection;
    private double repulsion = 1.15;

    private boolean grouped;
    private GroupAxis groupAxis = GroupAxis.TYPE;
    private String expandedGroup;
    private EntityType focusType;
    private int riskFilter;
    private final Set<Tlp> tlpFilter = EnumSet.allOf(Tlp.class);
    private boolean egoFocus;
    private String egoRoot;
    private final Deque<String> egoHistory = new ArrayDeque<>();
    private boolean legendVisible = true;
    private boolean edgeLabelsVisible;
    private String searchHighlight = "";

    private String hoveredEdgeKey;
    private String selectedEdgeKey;
    private String hoverGroupKey;

    private final Map<String, Point2D> groupCenters = new LinkedHashMap<>();
    private final Map<String, Double> groupRadii = new LinkedHashMap<>();
    private final Map<String, Point2D> groupNodePositions = new HashMap<>();
    private final Map<String, String> groupByNode = new HashMap<>();
    private Group lastExpanded;
    private List<Group> lastGroups = List.of();

    private final Map<String, Integer> communityCache = new HashMap<>();
    private String communityCacheKey = "";

    private final Tooltip tooltip = new Tooltip();
    private double backX, backY, backW, backH;

    public GraphCanvas(double width, double height, Consumer<GraphNode> onSelect) {
        super(width, height);
        this.onSelect = onSelect;
        this.animator = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (liveForce != null) {
                    boolean live = liveForce.step(graph, getWidth(), getHeight());
                    CollisionResolver.resolveAdaptive(graph, 6);
                    redraw();
                    if (!live) liveForce = null;
                }
            }
        };
        tooltip.setShowDelay(Duration.millis(260));
        tooltip.setHideDelay(Duration.millis(80));
        Tooltip.install(this, tooltip);
        setFocusTraversable(true);
        setAccessibleText("Grafo de entidades de la investigación");
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
        attachHandlers();
        setCursor(Cursor.DEFAULT);
    }

    public void setGraph(OrionGraph g) {
        this.graph = g == null ? new OrionGraph() : g;
        this.selectedKey = null;
        this.selectedKeys.clear();
        this.hiddenKeys.clear();
        this.hoverKey = null;
        this.panning = false;
        this.bandSelecting = false;
        boolean wasGrouped = this.grouped;
        this.grouped = false;
        this.focusType = null;
        this.expandedGroup = null;
        this.egoFocus = false;
        this.egoRoot = null;
        this.egoHistory.clear();
        this.riskFilter = 0;
        this.tlpFilter.clear();
        this.tlpFilter.addAll(EnumSet.allOf(Tlp.class));
        this.searchHighlight = "";
        this.selectedEdgeKey = null;
        this.hoveredEdgeKey = null;
        this.communityCache.clear();
        this.communityCacheKey = "";
        if (wasGrouped && onViewReset != null) onViewReset.run();
        if (getWidth() > 1 && getHeight() > 1) {
            relayout(currentLayout);
            fitView();
        } else {
            scheduleInitialLayout();
        }
    }

    private void scheduleInitialLayout() {
        final javafx.beans.value.ChangeListener<Number>[] holder = new javafx.beans.value.ChangeListener[2];
        Runnable run = () -> {
            if (getWidth() > 1 && getHeight() > 1 && graph.nodeCount() > 0) {
                widthProperty().removeListener(holder[0]);
                heightProperty().removeListener(holder[1]);
                relayout(currentLayout);
                fitView();
            }
        };
        holder[0] = (obs, o, n) -> run.run();
        holder[1] = (obs, o, n) -> run.run();
        widthProperty().addListener(holder[0]);
        heightProperty().addListener(holder[1]);
        redraw();
    }

    public OrionGraph getGraph() { return graph; }

    public void setMoveNeighbors(boolean moveNeighbors) { this.moveNeighbors = moveNeighbors; }

    public void setOnViewReset(Runnable onViewReset) { this.onViewReset = onViewReset; }

    public void setOnDeleteSelection(Runnable onDeleteSelection) {
        this.onDeleteSelection = onDeleteSelection;
    }

    public void setGrouped(boolean grouped) {
        boolean wasGrouped = this.grouped;
        this.grouped = grouped;
        this.focusType = null;
        this.selectedKey = null;
        this.selectedKeys.clear();
        this.hoverKey = null;
        this.hoveredEdgeKey = null;
        this.selectedEdgeKey = null;
        this.hoverGroupKey = null;
        this.expandedGroup = null;
        this.egoFocus = false;
        this.egoRoot = null;
        this.egoHistory.clear();
        this.bandSelecting = false;
        this.panning = false;
        if (grouped) {
            liveForce = null;
            animator.stop();
        }
        if (wasGrouped && !grouped && onViewReset != null) onViewReset.run();
        redraw();
    }

    public boolean isGrouped() { return grouped; }

    public GroupAxis getGroupAxis() { return groupAxis; }

    public void setGroupAxis(GroupAxis axis) {
        this.groupAxis = axis == null ? GroupAxis.TYPE : axis;
        this.expandedGroup = null;
        this.groupCenters.clear();
        this.groupRadii.clear();
        this.groupNodePositions.clear();
        this.groupByNode.clear();
        this.hoverGroupKey = null;
        redraw();
    }

    public void setRiskFilter(int minRisk) {
        this.riskFilter = Math.max(0, Math.min(100, minRisk));
        redraw();
    }

    public int getRiskFilter() { return riskFilter; }

    public void setTlpFilter(Collection<Tlp> levels) {
        tlpFilter.clear();
        if (levels == null || levels.isEmpty()) {
            tlpFilter.addAll(EnumSet.allOf(Tlp.class));
        } else {
            tlpFilter.addAll(levels);
        }
        redraw();
    }

    public Set<Tlp> getTlpFilter() { return Set.copyOf(tlpFilter); }

    public boolean isEgoFocus() { return egoFocus; }

    public void setEgoFocus(boolean enabled) {
        if (enabled) {
            if (selectedKey != null) focusNode(selectedKey);
        } else {
            exitEgoFocus();
        }
    }

    public void focusNode(String key) {
        GraphNode n = key == null ? null : graph.node(key).orElse(null);
        if (n == null) return;
        if (egoRoot != null && !egoRoot.equals(key)) {
            if (egoHistory.size() >= 12) egoHistory.removeLast();
            egoHistory.push(egoRoot);
        }
        boolean wasGrouped = grouped;
        egoRoot = key;
        egoFocus = true;
        grouped = false;
        if (wasGrouped && onViewReset != null) onViewReset.run();
        expandedGroup = null;
        focusType = null;
        hiddenKeys.remove(key);
        selectedKey = key;
        selectedKeys.clear();
        selectedKeys.add(key);
        selectedEdgeKey = null;
        if (onSelect != null) onSelect.accept(n);
        fitView();
    }

    public boolean canGoBack() { return !egoHistory.isEmpty(); }

    public void goBack() {
        if (egoHistory.isEmpty()) {
            exitEgoFocus();
            return;
        }
        egoRoot = egoHistory.pop();
        egoFocus = true;
        selectedKey = egoRoot;
        selectedKeys.clear();
        selectedKeys.add(egoRoot);
        fitView();
    }

    public void exitEgoFocus() {
        egoFocus = false;
        egoRoot = null;
        egoHistory.clear();
        fitView();
    }

    public boolean isLegendVisible() { return legendVisible; }

    public void setLegendVisible(boolean visible) {
        this.legendVisible = visible;
        redraw();
    }

    public boolean isEdgeLabelsVisible() { return edgeLabelsVisible; }

    public void setEdgeLabelsVisible(boolean visible) {
        this.edgeLabelsVisible = visible;
        redraw();
    }

    public void setSearchHighlight(String text) {
        this.searchHighlight = text == null ? "" : text.trim();
        redraw();
    }

    public String getEgoRoot() { return egoRoot; }

    public int getVisibleNodeCount() { return visibleNodes().size(); }

    public void showAll() {
        boolean wasGrouped = grouped;
        grouped = false;
        focusType = null;
        expandedGroup = null;
        egoFocus = false;
        egoRoot = null;
        egoHistory.clear();
        riskFilter = 0;
        tlpFilter.clear();
        tlpFilter.addAll(EnumSet.allOf(Tlp.class));
        selectedKey = null;
        selectedKeys.clear();
        hiddenKeys.clear();
        hoverKey = null;
        hoverGroupKey = null;
        selectedEdgeKey = null;
        hoveredEdgeKey = null;
        bandSelecting = false;
        panning = false;
        if (wasGrouped && onViewReset != null) onViewReset.run();
        redraw();
    }

    public void focusGroup(EntityType type) {
        if (type == null) return;
        grouped = true;
        groupAxis = GroupAxis.TYPE;
        expandedGroup = "TYPE:" + type.name();
        focusType = null;
        egoFocus = false;
        egoRoot = null;
        egoHistory.clear();
        selectedKey = null;
        selectedKeys.clear();
        hoverKey = null;
        hoverGroupKey = null;
        liveForce = null;
        animator.stop();
        redraw();
    }

    public void setRepulsion(double value) {
        repulsion = Math.max(0.5, Math.min(3.0, value));
        if (liveForce != null) liveForce.setRepulsion(activeRepulsion());
    }

    public void separate() {
        CollisionResolver.resolveAdaptive(graph, 120);
        redraw();
    }

    /** Detiene la animacion de fuerzas antes de una importacion masiva. */
    public void stopLiveLayout() {
        liveForce = null;
        animator.stop();
    }

    private double activeRepulsion() {
        return currentLayout == LayoutType.ORGANIC ? repulsion * 1.55 : repulsion;
    }

    public void relayout() { relayout(currentLayout); }

    public void relayout(LayoutType type) {
        currentLayout = type;
        liveForce = null;
        var layout = switch (type) {
            case FORCE_DIRECTED -> new ForceDirectedLayout(activeRepulsion(), 1.0, 0.02);
            case ORGANIC -> new ForceDirectedLayout(activeRepulsion(), 0.92, 0.035);
            default -> type.create();
        };
        layout.apply(graph, getWidth(), getHeight());
        CollisionResolver.resolveAdaptive(graph, 80);
        if (layout instanceof ForceDirectedLayout fdl && type == LayoutType.FORCE_DIRECTED && !grouped) {
            liveForce = fdl;
            animator.start();
        }
        redraw();
    }

    public void fitView() {
        if (grouped) {
            redraw();
            return;
        }
        List<GraphNode> visible = visibleNodes();
        if (visible.isEmpty()) {
            scale = 1;
            panX = 0;
            panY = 0;
            redraw();
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GraphNode n : visible) {
            minX = Math.min(minX, n.getX() - 80);
            minY = Math.min(minY, n.getY() - 80);
            maxX = Math.max(maxX, n.getX() + 80);
            maxY = Math.max(maxY, n.getY() + 80);
        }
        double w = Math.max(1, maxX - minX), h = Math.max(1, maxY - minY);
        scale = Math.min((getWidth() - 40) / w, (getHeight() - 40) / h);
        scale = Math.max(0.15, Math.min(2.0, scale));
        panX = (getWidth() - w * scale) / 2 - minX * scale;
        panY = (getHeight() - h * scale) / 2 - minY * scale;
        redraw();
    }

    public void zoom(double factor) {
        if (grouped) return;
        double cx = getWidth() / 2, cy = getHeight() / 2;
        scale = Math.max(0.1, Math.min(4, scale * factor));
        panX = cx - (cx - panX) * factor;
        panY = cy - (cy - panY) * factor;
        redraw();
    }

    public void centerOn(String key) {
        GraphNode node = key == null ? null : graph.node(key).orElse(null);
        if (node == null || grouped) return;
        panX = getWidth() / 2 - node.getX() * scale;
        panY = getHeight() / 2 - node.getY() * scale;
        redraw();
    }

    public String getSelectedKey() { return selectedKey; }

    public Set<String> getSelectedKeys() { return Set.copyOf(selectedKeys); }

    public List<GraphNode> getSelectedNodes() {
        List<GraphNode> nodes = new ArrayList<>();
        for (String key : selectedKeys) {
            graph.node(key).ifPresent(nodes::add);
        }
        return nodes;
    }

    public boolean isHidden(String key) { return key != null && hiddenKeys.contains(key); }

    public void setHidden(String key, boolean hidden) {
        if (key == null) return;
        if (hidden) {
            hiddenKeys.add(key);
            selectedKeys.remove(key);
            if (key.equals(selectedKey)) {
                selectedKey = selectedKeys.stream().findFirst().orElse(null);
                if (selectedKey != null) {
                    graph.node(selectedKey).ifPresent(n -> {
                        if (onSelect != null) onSelect.accept(n);
                    });
                }
            }
        } else {
            hiddenKeys.remove(key);
        }
        redraw();
    }

    public void clearSelection() {
        select(null);
    }

    public void select(String key) {
        GraphNode n = key == null ? null : graph.node(key).orElse(null);
        if (n != null && focusType != null && n.getEntity().getType() != focusType) {
            focusType = null;
            if (onViewReset != null) onViewReset.run();
        }
        if (n != null && egoFocus && !visibleNodes().contains(n)) {
            egoFocus = false;
            egoRoot = null;
            egoHistory.clear();
        }
        if (n != null && grouped) {
            String groupKey = groupByNode.get(n.getKey());
            if (groupKey == null) {
                buildGroups(visibleNodes());
                groupKey = groupByNode.get(n.getKey());
            }
            if (groupKey != null) expandedGroup = groupKey;
        }
        if (n != null) hiddenKeys.remove(n.getKey());
        this.selectedKey = n == null ? null : n.getKey();
        this.selectedKeys.clear();
        if (selectedKey != null) selectedKeys.add(selectedKey);
        this.selectedEdgeKey = null;
        this.hoverKey = null;
        this.bandSelecting = false;
        redraw();
        if (onSelect != null && n != null) onSelect.accept(n);
    }

    private void selectKeys(Collection<String> keys) {
        List<GraphNode> nodes = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                GraphNode node = key == null ? null : graph.node(key).orElse(null);
                if (node != null && !hiddenKeys.contains(key)) nodes.add(node);
            }
        }
        selectedKeys.clear();
        for (GraphNode node : nodes) selectedKeys.add(node.getKey());
        selectedKey = nodes.isEmpty() ? null : nodes.get(0).getKey();
        selectedEdgeKey = null;
        hoverKey = null;
        bandSelecting = false;
        redraw();
        if (onSelect != null && !nodes.isEmpty()) onSelect.accept(nodes.get(0));
    }

    private void toggleSelection(String key) {
        GraphNode node = key == null ? null : graph.node(key).orElse(null);
        if (node == null) return;
        if (selectedKeys.remove(key)) {
            selectedKey = selectedKeys.stream().findFirst().orElse(null);
        } else {
            selectedKeys.add(key);
            selectedKey = key;
        }
        selectedEdgeKey = null;
        redraw();
        GraphNode primary = selectedKey == null ? null : graph.node(selectedKey).orElse(null);
        if (onSelect != null && primary != null) onSelect.accept(primary);
    }

    private boolean passesFilters(GraphNode n) {
        return n.getEntity().getRiskScore() >= riskFilter
                && tlpFilter.contains(n.getEntity().getTlp());
    }

    private List<GraphNode> visibleNodes() {
        List<GraphNode> filtered = new ArrayList<>();
        for (GraphNode n : graph.getNodes()) {
            if (hiddenKeys.contains(n.getKey())) continue;
            if (!passesFilters(n)) continue;
            if (focusType != null && n.getEntity().getType() != focusType) continue;
            filtered.add(n);
        }
        if (!egoFocus || egoRoot == null) return filtered;
        Set<String> keep = new HashSet<>();
        keep.add(egoRoot);
        graph.neighbors(egoRoot).forEach(n -> keep.add(n.getKey()));
        return filtered.stream().filter(n -> keep.contains(n.getKey())).toList();
    }

    private Point2D toScreen(double gx, double gy) {
        return new Point2D(gx * scale + panX, gy * scale + panY);
    }

    private Point2D toGraph(double sx, double sy) {
        return new Point2D((sx - panX) / scale, (sy - panY) / scale);
    }

    private double nodeScreenRadius(GraphNode n) {
        return Math.max(5, Math.min(16, n.radius() * Math.max(0.55, scale)));
    }

    private GraphNode pick(double sx, double sy) {
        if (grouped) return pickGroupNode(sx, sy);
        GraphNode best = null;
        double bestD = Double.MAX_VALUE;
        for (GraphNode n : visibleNodes()) {
            Point2D p = toScreen(n.getX(), n.getY());
            double d = Math.hypot(sx - p.getX(), sy - p.getY());
            double hit = Math.max(11, nodeScreenRadius(n) + 4);
            if (d <= hit && d < bestD) {
                best = n;
                bestD = d;
            }
        }
        return best;
    }

    private GraphNode pickGroupNode(double sx, double sy) {
        GraphNode best = null;
        double bestD = 14;
        for (var entry : groupNodePositions.entrySet()) {
            Point2D p = entry.getValue();
            double d = Math.hypot(sx - p.getX(), sy - p.getY());
            if (d <= bestD) {
                bestD = d;
                best = graph.node(entry.getKey()).orElse(null);
            }
        }
        return best;
    }

    private List<String> nodesInBand() {
        double x1 = Math.min(bandStartX, bandEndX);
        double x2 = Math.max(bandStartX, bandEndX);
        double y1 = Math.min(bandStartY, bandEndY);
        double y2 = Math.max(bandStartY, bandEndY);
        List<String> hits = new ArrayList<>();
        for (GraphNode n : visibleNodes()) {
            Point2D p = toScreen(n.getX(), n.getY());
            if (p.getX() >= x1 && p.getX() <= x2 && p.getY() >= y1 && p.getY() <= y2) {
                hits.add(n.getKey());
            }
        }
        return hits;
    }

    private void attachHandlers() {
        setOnScroll((ScrollEvent e) -> {
            if (grouped) { e.consume(); return; }
            double factor = e.getDeltaY() > 0 ? 1.12 : 1 / 1.12;
            double mx = e.getX(), my = e.getY();
            scale = Math.max(0.1, Math.min(4, scale * factor));
            panX = mx - (mx - panX) * factor;
            panY = my - (my - panY) * factor;
            redraw();
            e.consume();
        });

        setOnMousePressed(e -> {
            requestFocus();
            pressX = lastX = e.getX();
            pressY = lastY = e.getY();
            dragging = false;
            panning = false;
            bandSelecting = false;
            hoveredEdgeKey = null;
            if (grouped) {
                dragNode = null;
                return;
            }
            if (e.getButton() == MouseButton.MIDDLE
                    || (e.getButton() == MouseButton.PRIMARY && e.isAltDown())) {
                panning = true;
                dragNode = null;
                setCursor(Cursor.CLOSED_HAND);
                return;
            }
            dragNode = e.getButton() == MouseButton.PRIMARY ? pick(e.getX(), e.getY()) : null;
            if (dragNode != null) {
                dragNode.setFixed(true);
                if (moveNeighbors) {
                    dragNeighbors = graph.neighbors(dragNode.getKey()).stream()
                            .filter(n -> n != dragNode).toList();
                    dragNeighbors.forEach(n -> n.setFixed(true));
                }
                setCursor(Cursor.CLOSED_HAND);
            } else if (e.getButton() == MouseButton.PRIMARY && !backHit(e.getX(), e.getY())) {
                bandSelecting = true;
                bandStartX = bandEndX = e.getX();
                bandStartY = bandEndY = e.getY();
                setCursor(Cursor.CROSSHAIR);
                hideTooltip();
            }
        });

        setOnMouseDragged(e -> {
            if (grouped) return;
            if (panning) {
                panX += e.getX() - lastX;
                panY += e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                redraw();
                return;
            }
            if (bandSelecting) {
                if (Math.hypot(e.getX() - pressX, e.getY() - pressY) > 3) dragging = true;
                bandEndX = e.getX();
                bandEndY = e.getY();
                redraw();
                return;
            }
            if (Math.hypot(e.getX() - pressX, e.getY() - pressY) > 3) dragging = true;
            double dx = e.getX() - lastX, dy = e.getY() - lastY;
            lastX = e.getX();
            lastY = e.getY();
            if (dragNode != null) {
                dragNode.setX(dragNode.getX() + dx / scale);
                dragNode.setY(dragNode.getY() + dy / scale);
                for (GraphNode n : dragNeighbors) {
                    n.setX(n.getX() + dx / scale);
                    n.setY(n.getY() + dy / scale);
                }
            }
            redraw();
        });

        setOnMouseReleased(e -> {
            if (grouped) {
                if (dragging || e.getButton() != MouseButton.PRIMARY) return;
                GraphNode node = pickGroupNode(e.getX(), e.getY());
                if (node != null) {
                    select(node.getKey());
                    return;
                }
                String key = groupAt(e.getX(), e.getY());
                if (key != null) {
                    expandedGroup = key.equals(expandedGroup) ? null : key;
                    selectedKey = null;
                    selectedKeys.clear();
                    hoverKey = null;
                    redraw();
                } else if (expandedGroup != null) {
                    expandedGroup = null;
                    selectedKey = null;
                    selectedKeys.clear();
                    hoverKey = null;
                    redraw();
                }
                return;
            }
            if (panning) {
                panning = false;
                setCursor(Cursor.DEFAULT);
                return;
            }
            if (bandSelecting) {
                boolean hadDrag = Math.hypot(e.getX() - pressX, e.getY() - pressY) > 3;
                bandSelecting = false;
                if (hadDrag) {
                    List<String> hits = nodesInBand();
                    if (e.isShiftDown()) {
                        Set<String> union = new HashSet<>(selectedKeys);
                        union.addAll(hits);
                        selectKeys(union);
                    } else {
                        selectKeys(hits);
                    }
                } else {
                    GraphEdge edge = edgeAt(e.getX(), e.getY());
                    if (edge != null) {
                        if (!e.isShiftDown()) {
                            selectedKey = null;
                            selectedKeys.clear();
                        }
                        selectedEdgeKey = edgeKey(edge);
                        redraw();
                    } else if (!e.isShiftDown()) {
                        select(null);
                    }
                }
                setCursor(Cursor.DEFAULT);
                return;
            }
            if (dragNode != null) {
                dragNode.setFixed(false);
                dragNode.setVx(0);
                dragNode.setVy(0);
                dragNode = null;
                for (GraphNode n : dragNeighbors) {
                    n.setFixed(false);
                    n.setVx(0);
                    n.setVy(0);
                }
                dragNeighbors = List.of();
                setCursor(Cursor.DEFAULT);
            }
            if (!dragging && e.getButton() == MouseButton.PRIMARY) {
                if (backHit(e.getX(), e.getY())) {
                    goBack();
                    return;
                }
                GraphNode n = pick(e.getX(), e.getY());
                if (n != null) {
                    if (e.isShiftDown()) {
                        toggleSelection(n.getKey());
                    } else {
                        select(n.getKey());
                    }
                    return;
                }
                GraphEdge edge = edgeAt(e.getX(), e.getY());
                if (edge != null) {
                    selectedEdgeKey = edgeKey(edge);
                    selectedKey = null;
                    selectedKeys.clear();
                    redraw();
                } else {
                    selectedEdgeKey = null;
                    select(null);
                }
            }
        });

        setOnMouseMoved(e -> {
            if (grouped) {
                GraphNode node = pickGroupNode(e.getX(), e.getY());
                String groupKey = node == null ? groupAt(e.getX(), e.getY()) : null;
                String nodeKey = node == null ? null : node.getKey();
                if (!Objects.equals(nodeKey, hoverKey)
                        || !Objects.equals(groupKey, hoverGroupKey)) {
                    hoverKey = nodeKey;
                    hoverGroupKey = groupKey;
                    redraw();
                }
                if (node != null) {
                    setCursor(Cursor.HAND);
                    showNodeTooltip(node);
                } else if (groupKey != null) {
                    setCursor(Cursor.HAND);
                    showGroupTooltip(groupKey);
                } else {
                    setCursor(Cursor.DEFAULT);
                    hideTooltip();
                }
                return;
            }
            GraphNode n = pick(e.getX(), e.getY());
            GraphEdge edge = n == null ? edgeAt(e.getX(), e.getY()) : null;
            String k = n == null ? null : n.getKey();
            String ek = edge == null ? null : edgeKey(edge);
            if (!Objects.equals(k, hoverKey) || !Objects.equals(ek, hoveredEdgeKey)) {
                hoverKey = k;
                hoveredEdgeKey = ek;
                setCursor(n != null ? Cursor.HAND : edge != null ? Cursor.CROSSHAIR : Cursor.DEFAULT);
                redraw();
            }
            if (n != null) {
                showNodeTooltip(n);
            } else if (edge != null) {
                tooltip.setText(edge.getLabel() + "\n" + edge.getRelation().label());
            } else {
                hideTooltip();
            }
        });

        addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                GraphNode n = pick(e.getX(), e.getY());
                if (n != null) focusNode(n.getKey());
            }
        });

        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                if (grouped && expandedGroup != null) {
                    expandedGroup = null;
                    selectedKey = null;
                    hoverKey = null;
                    redraw();
                } else if (grouped) {
                    setGrouped(false);
                } else if (egoFocus) {
                    goBack();
                } else {
                    select(null);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && selectedKey != null) {
                focusNode(selectedKey);
                e.consume();
            } else if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)
                    && !selectedKeys.isEmpty()) {
                if (onDeleteSelection != null) onDeleteSelection.run();
                e.consume();
            } else if (e.getCode() == KeyCode.L) {
                setLegendVisible(!legendVisible);
                e.consume();
            } else if (e.getCode() == KeyCode.G) {
                setGrouped(!grouped);
                e.consume();
            } else if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.EQUALS
                    || e.getCode() == KeyCode.ADD) {
                zoom(1.15);
                e.consume();
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                zoom(1 / 1.15);
                e.consume();
            } else if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT
                    || e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                if (grouped) return;
                double step = 34;
                if (e.getCode() == KeyCode.LEFT) panX += step;
                if (e.getCode() == KeyCode.RIGHT) panX -= step;
                if (e.getCode() == KeyCode.UP) panY += step;
                if (e.getCode() == KeyCode.DOWN) panY -= step;
                redraw();
                e.consume();
            }
        });
    }

    public GraphNode nodeAt(double sx, double sy) { return pick(sx, sy); }

    private boolean backHit(double x, double y) {
        return backW > 0 && x >= backX && x <= backX + backW && y >= backY && y <= backY + backH;
    }

    private void showNodeTooltip(GraphNode n) {
        var entity = n.getEntity();
        String sources = entity.getSources().isEmpty() ? "" : "\nFuentes: " + entity.getSources().size();
        tooltip.setText(entity.getType().label() + ": " + entity.getValue()
                + "\nRiesgo " + Math.round(entity.getRiskScore()) + " · TLP:" + entity.getTlp().name()
                + sources + "\nDoble clic: enfocar vecindad");
    }

    private void showGroupTooltip(String key) {
        Group group = findGroup(key);
        if (group == null) {
            hideTooltip();
            return;
        }
        String state = key.equals(expandedGroup) ? "Clic para plegar" : "Clic para expandir";
        tooltip.setText(group.title() + " · " + group.nodes().size() + " entidades"
                + "\nRiesgo máximo " + Math.round(group.maxRisk())
                + " · " + group.critical() + " críticas"
                + "\n" + state);
    }

    private void hideTooltip() {
        if (!tooltip.getText().isEmpty()) tooltip.setText("");
        tooltip.hide();
    }

    public void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        g.setFill(BG);
        g.fillRect(0, 0, w, h);
        drawGrid(g, w, h);

        if (grouped) {
            drawGroupSummary(g, w, h);
            drawOverlays(g, w, h);
            return;
        }

        List<GraphNode> visible = visibleNodes();
        if (visible.isEmpty()) {
            drawEmpty(g, w, h);
            drawOverlays(g, w, h);
            return;
        }
        Set<String> visibleKeys = new HashSet<>();
        visible.forEach(n -> visibleKeys.add(n.getKey()));
        Set<String> neighborKeys = new HashSet<>();
        for (String key : selectedKeys) {
            graph.neighbors(key).stream().map(GraphNode::getKey)
                    .filter(visibleKeys::contains).forEach(neighborKeys::add);
        }
        List<String> neighbors = new ArrayList<>(neighborKeys);
        boolean hasSelection = !selectedKeys.isEmpty();

        for (GraphEdge e : graph.getEdges()) {
            if (!visibleKeys.contains(e.getSource().getKey())
                    || !visibleKeys.contains(e.getTarget().getKey())) continue;
            boolean selectedNodeEdge = selectedKeys.contains(e.getSource().getKey())
                    || selectedKeys.contains(e.getTarget().getKey());
            drawEdge(g, e, hasSelection && !selectedNodeEdge);
        }
        for (GraphNode n : visible) {
            boolean dim = hasSelection && !selectedKeys.contains(n.getKey())
                    && !neighborKeys.contains(n.getKey());
            drawNode(g, n, dim);
        }
        drawNodeLabels(g, visible, neighbors);
        drawSelectionBand(g);
        drawOverlays(g, w, h);
    }

    private void drawSelectionBand(GraphicsContext g) {
        if (!bandSelecting) return;
        double x = Math.min(bandStartX, bandEndX);
        double y = Math.min(bandStartY, bandEndY);
        double w = Math.abs(bandEndX - bandStartX);
        double h = Math.abs(bandEndY - bandStartY);
        if (w < 1 && h < 1) return;
        g.setFill(Color.color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 0.12));
        g.fillRect(x, y, w, h);
        g.setStroke(Color.color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 0.85));
        g.setLineWidth(1.2);
        g.setLineDashes(5, 3);
        g.strokeRect(x, y, w, h);
        g.setLineDashes(0);
    }

    private void drawGrid(GraphicsContext g, double w, double h) {
        g.setStroke(Color.web("#161b22"));
        g.setLineWidth(1);
        for (double x = 0; x < w; x += 42) g.strokeLine(x, 0, x, h);
        for (double y = 0; y < h; y += 42) g.strokeLine(0, y, w, y);
    }

    private void drawEmpty(GraphicsContext g, double w, double h) {
        g.setFill(MUTED);
        g.setFont(Font.font("System", 13));
        g.setTextAlign(TextAlignment.CENTER);
        String msg = graph.nodeCount() == 0
                ? "Sin entidades. Añade un dominio o ejecuta una transformación."
                : "Ninguna entidad coincide con los filtros actuales.";
        g.fillText(msg, w / 2, h / 2 - 6);
        if (graph.nodeCount() > 0) {
            g.setFont(Font.font("System", 11));
            g.fillText("Visualización > Mostrar todas las entidades", w / 2, h / 2 + 16);
        }
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawEdge(GraphicsContext g, GraphEdge e, boolean dim) {
        Point2D a = toScreen(e.getSource().getX(), e.getSource().getY());
        Point2D b = toScreen(e.getTarget().getX(), e.getTarget().getY());
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        String key = edgeKey(e);
        boolean hot = key.equals(hoveredEdgeKey) || key.equals(selectedEdgeKey);
        boolean selectedNodeEdge = selectedKeys.contains(e.getSource().getKey())
                || selectedKeys.contains(e.getTarget().getKey());
        Color color = hot ? ACCENT : selectedNodeEdge ? Color.web("#79c0ff")
                : dim ? Color.web("#2a3142") : Color.web("#46536b");
        double width = hot ? 2.2 : selectedNodeEdge ? 1.8 : dim ? 1 : 1.2;
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                hot ? 0.95 : dim ? 0.25 : selectedNodeEdge ? 0.75 : 0.45));
        g.setLineWidth(width);
        double r1 = nodeScreenRadius(e.getSource()) + 2;
        double r2 = nodeScreenRadius(e.getTarget()) + 2;
        double ux = dx / len, uy = dy / len;
        double x1 = a.getX() + ux * r1, y1 = a.getY() + uy * r1;
        double x2 = b.getX() - ux * r2, y2 = b.getY() - uy * r2;
        g.strokeLine(x1, y1, x2, y2);

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double ah = hot ? 8 : 6;
        g.setFill(g.getStroke());
        g.fillPolygon(new double[]{x2, x2 - Math.cos(angle - 0.45) * ah, x2 - Math.cos(angle + 0.45) * ah},
                new double[]{y2, y2 - Math.sin(angle - 0.45) * ah, y2 - Math.sin(angle + 0.45) * ah}, 3);

        boolean label = edgeLabelsVisible || hot
                || (selectedNodeEdge && scale >= 0.85);
        if (label) {
            double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
            drawBadge(g, clip(e.getLabel(), 24), mx, my - 8,
                    hot ? ACCENT : Color.web("#8b949e"), 9.5);
        }
    }

    private void drawNode(GraphicsContext g, GraphNode n, boolean dim) {
        Point2D p = toScreen(n.getX(), n.getY());
        double r = nodeScreenRadius(n);
        if (p.getX() < -r || p.getY() < -r || p.getX() > getWidth() + r || p.getY() > getHeight() + r) return;
        Color type = Color.web(n.getEntity().getType().color());
        double alpha = dim ? 0.2 : 1;
        RiskScore.Level level = levelOf(n.getEntity().getRiskScore());
        boolean selected = selectedKeys.contains(n.getKey());
        boolean hovered = n.getKey().equals(hoverKey);
        boolean match = isSearchMatch(n);

        if (n.getEntity().getRiskScore() >= 75 || selected) {
            g.setStroke(Color.color(Color.web(level.color()).getRed(),
                    Color.web(level.color()).getGreen(), Color.web(level.color()).getBlue(),
                    selected ? 0.8 : 0.35 * alpha));
            g.setLineWidth(selected ? 2.5 : 1.5);
            g.strokeOval(p.getX() - r - 4, p.getY() - r - 4, 2 * r + 8, 2 * r + 8);
        }
        g.setFill(Color.color(BG.getRed(), BG.getGreen(), BG.getBlue(), alpha));
        g.fillOval(p.getX() - r - 2, p.getY() - r - 2, 2 * r + 4, 2 * r + 4);
        g.setFill(Color.color(type.getRed(), type.getGreen(), type.getBlue(), 0.22 * alpha));
        g.fillOval(p.getX() - r, p.getY() - r, 2 * r, 2 * r);
        g.setStroke(Color.color(type.getRed(), type.getGreen(), type.getBlue(), 0.9 * alpha));
        g.setLineWidth(1.3);
        g.strokeOval(p.getX() - r, p.getY() - r, 2 * r, 2 * r);
        Icons.drawEntity(g, n.getEntity().getType(), p.getX(), p.getY(), r * 1.35,
                Color.color(type.getRed(), type.getGreen(), type.getBlue(), alpha));

        if (selected) {
            g.setStroke(Color.WHITE);
            g.setLineWidth(2);
            g.strokeOval(p.getX() - r - 2, p.getY() - r - 2, 2 * r + 4, 2 * r + 4);
        } else if (hovered || match) {
            g.setStroke(match ? Color.web("#39c5cf") : ACCENT);
            g.setLineWidth(1.8);
            g.strokeOval(p.getX() - r - 3, p.getY() - r - 3, 2 * r + 6, 2 * r + 6);
        }
        Color tlp = Color.web(n.getEntity().getTlp().color());
        g.setFill(tlp);
        g.fillOval(p.getX() + r * 0.45, p.getY() - r - 3, 5, 5);
    }

    private void drawNodeLabels(GraphicsContext g, List<GraphNode> visible, List<String> neighbors) {
        List<LabelBox> labels = new ArrayList<>();
        List<Rectangle2D> occupied = new ArrayList<>();
        List<GraphNode> ordered = new ArrayList<>(visible);
        ordered.sort(Comparator.comparingInt(n -> -labelPriority(n, neighbors)));
        int threshold = visible.size() > 80 ? 55 : visible.size() > 38 ? 32 : 0;
        if (scale < 0.55) threshold = Math.max(threshold, 80);
        int drawn = 0;
        int maxLabels = scale < 0.45 ? 3 : 70;
        for (GraphNode n : ordered) {
            int priority = labelPriority(n, neighbors);
            if (priority < threshold && !selectedKeys.contains(n.getKey()) && !n.getKey().equals(hoverKey)) {
                continue;
            }
            if (drawn++ >= maxLabels) break;
            Point2D p = toScreen(n.getX(), n.getY());
            double r = nodeScreenRadius(n);
            String text = clip(n.getEntity().getLabel(), scale < 0.8 ? 18 : 30);
            if (text.isBlank()) continue;
            boolean strong = selectedKeys.contains(n.getKey()) || n.getKey().equals(hoverKey);
            Font font = Font.font("System", strong ? FontWeight.BOLD : FontWeight.NORMAL,
                    Math.max(10, Math.min(12, 10.5 * Math.max(0.8, scale))));
            double tw = textWidth(text, font);
            double th = 13;
            double cx = p.getX();
            double cy = p.getY() + r + 6;
            Rectangle2D box = new Rectangle2D(cx - tw / 2 - 4, cy - 1, tw + 8, th + 4);
            if (!strong && intersects(box, occupied)) {
                box = new Rectangle2D(cx - tw / 2 - 4, p.getY() - r - th - 7, tw + 8, th + 4);
            }
            if (!strong && intersects(box, occupied)) {
                box = new Rectangle2D(p.getX() + r + 7, p.getY() - th / 2, tw + 8, th + 4);
            }
            if (!strong && intersects(box, occupied)) {
                box = new Rectangle2D(p.getX() - r - tw - 11, p.getY() - th / 2, tw + 8, th + 4);
            }
            if (!strong && intersects(box, occupied)) continue;
            occupied.add(box);
            Color color = strong ? Color.WHITE : Color.web("#c9d1d9");
            labels.add(new LabelBox(text, box, font, color, strong));
        }
        for (LabelBox l : labels) drawLabelBox(g, l);
    }

    private int labelPriority(GraphNode n, List<String> neighbors) {
        if (n.getKey().equals(selectedKey)) return 100;
        if (selectedKeys.contains(n.getKey())) return 95;
        if (n.getKey().equals(hoverKey)) return 98;
        if (isSearchMatch(n)) return 88;
        if (n.getEntity().getRiskScore() >= 80) return 72;
        if (neighbors.contains(n.getKey())) return 62;
        if (graph.degree(n.getKey()) >= 5) return 52;
        if (n.getEntity().getRiskScore() >= 50) return 40;
        return 10;
    }

    private void drawLabelBox(GraphicsContext g, LabelBox l) {
        g.setFill(l.strong() ? Color.web("#0d1117", 0.94) : Color.web("#0d1117", 0.72));
        g.fillRoundRect(l.box().getMinX(), l.box().getMinY(),
                l.box().getWidth(), l.box().getHeight(), 5, 5);
        if (l.strong()) {
            g.setStroke(BORDER);
            g.setLineWidth(1);
            g.strokeRoundRect(l.box().getMinX(), l.box().getMinY(),
                    l.box().getWidth(), l.box().getHeight(), 5, 5);
        }
        g.setFill(l.color());
        g.setFont(l.font());
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(l.text(), l.box().getMinX() + l.box().getWidth() / 2,
                l.box().getMinY() + l.box().getHeight() / 2 + 4);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawGroupSummary(GraphicsContext g, double w, double h) {
        List<GraphNode> visible = visibleNodes();
        groupCenters.clear();
        groupRadii.clear();
        groupNodePositions.clear();
        groupByNode.clear();
        lastExpanded = null;
        if (visible.isEmpty()) {
            lastGroups = List.of();
            drawEmpty(g, w, h);
            return;
        }
        List<Group> groups = buildGroups(visible);
        lastGroups = groups;
        if (expandedGroup != null && groups.stream().noneMatch(gr -> gr.key().equals(expandedGroup))) {
            expandedGroup = null;
        }
        if (expandedGroup != null) {
            Group expanded = findGroup(expandedGroup);
            if (expanded != null) {
                double top = 78;
                double bottom = legendVisible ? 118 : 36;
                double radius = Math.max(90, Math.min(260,
                        Math.min((w - 100) / 2, (h - top - bottom - 50) / 2)));
                Placed p = new Placed(expanded, new Point2D(w / 2, top + (h - top - bottom) / 2), radius);
                groupCenters.put(expanded.key(), p.center());
                groupRadii.put(expanded.key(), p.radius());
                for (GraphNode n : expanded.nodes()) groupByNode.put(n.getKey(), expanded.key());
                lastExpanded = expanded;
                drawExpandedGroup(g, p, visible);
                return;
            }
        }
        List<Placed> placed = layoutGroups(groups, w, h);
        for (Placed p : placed) {
            groupCenters.put(p.group().key(), p.center());
            groupRadii.put(p.group().key(), p.radius());
            for (GraphNode n : p.group().nodes()) groupByNode.put(n.getKey(), p.group().key());
        }
        drawGroupEdges(g, placed, visible);
        for (Placed p : placed) drawCollapsedGroup(g, p);
    }

    private List<Group> buildGroups(List<GraphNode> visible) {
        groupByNode.clear();
        Map<String, List<GraphNode>> buckets = new LinkedHashMap<>();
        for (GraphNode n : visible) {
            buckets.computeIfAbsent(groupKeyFor(n), k -> new ArrayList<>()).add(n);
        }
        if (groupAxis == GroupAxis.TYPE || groupAxis == GroupAxis.COMMUNITY) {
            List<GraphNode> singles = new ArrayList<>();
            Map<String, List<GraphNode>> merged = new LinkedHashMap<>();
            for (var entry : buckets.entrySet()) {
                if (entry.getValue().size() == 1) singles.addAll(entry.getValue());
                else merged.put(entry.getKey(), entry.getValue());
            }
            if (!singles.isEmpty()) {
                if (merged.isEmpty()) merged.put("OTHER", singles);
                else merged.put("OTHER", singles);
            }
            buckets = merged;
        }

        List<Group> groups = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            List<GraphNode> nodes = new ArrayList<>(entry.getValue());
            nodes.sort(Comparator.comparingDouble((GraphNode n) -> n.getEntity().getRiskScore()).reversed()
                    .thenComparing(n -> n.getEntity().getLabel()));
            double maxRisk = nodes.stream().mapToDouble(n -> n.getEntity().getRiskScore()).max().orElse(0);
            int critical = (int) nodes.stream().filter(n -> n.getEntity().getRiskScore() >= 80).count();
            int high = (int) nodes.stream().filter(n -> n.getEntity().getRiskScore() >= 60).count();
            String title;
            String subtitle;
            Color color;
            if (entry.getKey().equals("OTHER")) {
                title = "Otros";
                subtitle = "entidades sueltas";
                color = Color.web("#8b949e");
            } else {
                switch (groupAxis) {
                    case TYPE -> {
                        EntityType type = EntityType.valueOf(entry.getKey().substring(5));
                        title = type.label();
                        subtitle = nodes.size() + " entidades";
                        color = Color.web(type.color());
                    }
                    case RISK -> {
                        RiskScore.Level level = RiskScore.Level.valueOf(entry.getKey().substring(5));
                        title = level.label();
                        subtitle = riskRange(level);
                        color = Color.web(level.color());
                    }
                    case TLP -> {
                        Tlp tlp = Tlp.valueOf(entry.getKey().substring(4));
                        title = "TLP:" + tlp.name();
                        subtitle = tlp.description();
                        color = Color.web(tlp.color());
                    }
                    case COMMUNITY -> {
                        int id = Integer.parseInt(entry.getKey().substring(5));
                        GraphNode top = nodes.get(0);
                        title = communityTitle(nodes, id);
                        subtitle = "Comunidad " + (id + 1) + " · " + clip(top.getEntity().getLabel(), 26);
                        color = Color.web(top.getEntity().getType().color());
                    }
                    default -> throw new IllegalStateException();
                }
            }
            groups.add(new Group(entry.getKey(), title, subtitle, color, nodes, maxRisk, critical, high));
        }
        groups.sort((a, b) -> {
            boolean ao = a.key().equals("OTHER"), bo = b.key().equals("OTHER");
            if (ao != bo) return ao ? 1 : -1;
            int c = Double.compare(b.maxRisk(), a.maxRisk());
            if (c != 0) return c;
            c = Integer.compare(b.nodes().size(), a.nodes().size());
            if (c != 0) return c;
            return a.title().compareToIgnoreCase(b.title());
        });
        for (Group group : groups) {
            for (GraphNode n : group.nodes()) groupByNode.put(n.getKey(), group.key());
        }
        return groups;
    }

    private String groupKeyFor(GraphNode n) {
        return switch (groupAxis) {
            case TYPE -> "TYPE:" + n.getEntity().getType().name();
            case RISK -> "RISK:" + levelOf(n.getEntity().getRiskScore()).name();
            case TLP -> "TLP:" + n.getEntity().getTlp().name();
            case COMMUNITY -> "COMM:" + communityMap().getOrDefault(n.getKey(), 0);
        };
    }

    private Map<String, Integer> communityMap() {
        String key = graph.nodeCount() + ":" + graph.edgeCount();
        if (!key.equals(communityCacheKey)) {
            communityCache.clear();
            communityCache.putAll(CommunityDetector.detect(graph).communities());
            communityCacheKey = key;
        }
        return communityCache;
    }

    private String communityTitle(List<GraphNode> nodes, int id) {
        GraphNode top = nodes.get(0);
        EntityType type = top.getEntity().getType();
        if (type == EntityType.THREAT_ACTOR || type == EntityType.APT || type == EntityType.CAMPAIGN
                || type == EntityType.MALWARE || type == EntityType.INCIDENT) {
            return clip(top.getEntity().getLabel(), 24);
        }
        return "Comunidad " + (id + 1);
    }

    private List<Placed> layoutGroups(List<Group> groups, double w, double h) {
        double margin = 26;
        double top = 78;
        double bottom = legendVisible ? (h < 600 ? 108 : 126) : 42;
        if (h < 500) {
            top = 54;
            bottom = legendVisible ? 100 : 22;
        }
        double areaW = Math.max(120, w - margin * 2);
        double areaH = Math.max(120, h - top - bottom);
        double gap = 18;
        List<Placed> raw = new ArrayList<>();
        double x = 0, y = 0, rowH = 0, maxX = 0, maxY = 0;
        for (Group group : groups) {
            double radius = groupRadius(group);
            double cellW = 2 * radius + 52;
            double cellH = 2 * radius + 68;
            if (x > 0 && x + cellW > areaW) {
                x = 0;
                y += rowH + gap;
                rowH = 0;
            }
            double cx = x + cellW / 2;
            double cy = y + 36 + radius;
            raw.add(new Placed(group, new Point2D(cx, cy), radius));
            maxX = Math.max(maxX, x + cellW);
            maxY = Math.max(maxY, y + cellH);
            rowH = Math.max(rowH, cellH);
            x += cellW + gap;
        }
        double usedW = Math.max(1, maxX), usedH = Math.max(1, maxY);
        double fit = Math.min(1, Math.min(areaW / usedW, areaH / usedH));
        double offX = margin + (areaW - usedW * fit) / 2;
        double offY = top + (areaH - usedH * fit) / 2;
        List<Placed> placed = new ArrayList<>();
        for (Placed p : raw) {
            placed.add(new Placed(p.group(),
                    new Point2D(offX + p.center().getX() * fit, offY + p.center().getY() * fit),
                    Math.max(24, p.radius() * fit)));
        }
        return placed;
    }

    private double groupRadius(Group group) {
        double n = group.nodes().size();
        if (group.key().equals(expandedGroup)) {
            return Math.min(270, 108 + Math.sqrt(n) * 23);
        }
        return Math.min(78, 36 + Math.sqrt(n) * 8);
    }

    private void drawGroupEdges(GraphicsContext g, List<Placed> placed, List<GraphNode> visible) {
        Map<String, Link> links = new LinkedHashMap<>();
        Map<String, Placed> byKey = new HashMap<>();
        for (Placed p : placed) byKey.put(p.group().key(), p);
        for (GraphEdge e : graph.getEdges()) {
            String ga = groupByNode.get(e.getSource().getKey());
            String gb = groupByNode.get(e.getTarget().getKey());
            if (ga == null || gb == null || ga.equals(gb)) continue;
            boolean direct = ga.compareTo(gb) <= 0;
            String a = direct ? ga : gb;
            String b = direct ? gb : ga;
            Link link = links.computeIfAbsent(a + "\u0000" + b, k -> new Link(a, b));
            if (direct) link.ab++;
            else link.ba++;
        }
        for (Link link : links.values()) {
            Placed pa = byKey.get(link.a);
            Placed pb = byKey.get(link.b);
            if (pa == null || pb == null) continue;
            Point2D a = pa.center(), b = pb.center();
            double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
            double len = Math.hypot(dx, dy);
            if (len < 1) continue;
            int total = link.ab + link.ba;
            g.setStroke(Color.web("#46536b", total > 2 ? 0.35 : 0.22));
            g.setLineWidth(1 + Math.min(2.5, Math.log1p(total) * 0.8));
            double r1 = pa.radius() * 0.88, r2 = pb.radius() * 0.88;
            double ux = dx / len, uy = dy / len;
            double x1 = a.getX() + ux * r1, y1 = a.getY() + uy * r1;
            double x2 = b.getX() - ux * r2, y2 = b.getY() - uy * r2;
            g.strokeLine(x1, y1, x2, y2);
            if (total > 1) {
                drawBadge(g, total + (link.ab > 0 && link.ba > 0 ? " ↔" : ""),
                        (x1 + x2) / 2, (y1 + y2) / 2 - 7, MUTED, 9);
            }
            if (link.ab == 0 || link.ba == 0) {
                double t = 0.68;
                double ax = x1 + (x2 - x1) * t, ay = y1 + (y2 - y1) * t;
                double angle = Math.atan2(y2 - y1, x2 - x1);
                g.setFill(Color.web("#46536b", 0.7));
                g.fillPolygon(new double[]{ax, ax - Math.cos(angle - 0.45) * 6, ax - Math.cos(angle + 0.45) * 6},
                        new double[]{ay, ay - Math.sin(angle - 0.45) * 6, ay - Math.sin(angle + 0.45) * 6}, 3);
            }
        }
    }

    private void drawCollapsedGroup(GraphicsContext g, Placed p) {
        Group group = p.group();
        Point2D c = p.center();
        double r = p.radius();
        Color color = group.color();
        g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.14));
        g.fillOval(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.82));
        g.setLineWidth(1.6);
        g.strokeOval(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.95));
        g.setFont(Font.font("System", FontWeight.BOLD, Math.max(16, Math.min(26, 15 + r * 0.12))));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(String.valueOf(group.nodes().size()), c.getX(), c.getY() + 7);
        g.setFont(Font.font("System", FontWeight.BOLD, 11));
        g.setFill(TEXT);
        g.fillText(clip(group.title(), 24), c.getX(), c.getY() + r + 17);
        g.setFont(Font.font("System", 9.5));
        g.setFill(MUTED);
        String detail = group.critical() > 0 ? group.critical() + " críticas · máx. " + Math.round(group.maxRisk())
                : "máx. " + Math.round(group.maxRisk()) + " · " + group.subtitle();
        g.fillText(clip(detail, 28), c.getX(), c.getY() + r + 30);
        g.setTextAlign(TextAlignment.LEFT);
        if (group.critical() > 0) {
            double bx = c.getX() + r * 0.62, by = c.getY() - r * 0.72;
            drawBadge(g, group.critical() + "!", bx, by, CRITICAL, 9.5);
        }
        if (group.key().equals(hoverGroupKey)) {
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.4);
            g.strokeOval(c.getX() - r - 3, c.getY() - r - 3, 2 * r + 6, 2 * r + 6);
        }
    }

    private void drawExpandedGroup(GraphicsContext g, Placed p, List<GraphNode> visible) {
        Group group = p.group();
        Point2D c = p.center();
        double r = p.radius();
        Color color = group.color();
        g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.075));
        g.fillOval(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.9));
        g.setLineWidth(1.8);
        g.setLineDashes(7, 5);
        g.strokeOval(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setLineDashes(0);

        g.setFill(TEXT);
        g.setFont(Font.font("System", FontWeight.BOLD, 13));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(clip(group.title(), 30), c.getX(), c.getY() - r - 23);
        g.setFill(MUTED);
        g.setFont(Font.font("System", 10));
        String detail = group.nodes().size() + " entidades · máx. " + Math.round(group.maxRisk())
                + " · " + group.critical() + " críticas";
        g.fillText(clip(detail, 42), c.getX(), c.getY() - r - 8);
        g.setTextAlign(TextAlignment.LEFT);

        arrangeGroupNodes(group, c, r);
        Set<String> inside = new HashSet<>();
        group.nodes().forEach(n -> inside.add(n.getKey()));
        g.setLineWidth(1);
        for (GraphEdge e : graph.getEdges()) {
            if (!inside.contains(e.getSource().getKey()) || !inside.contains(e.getTarget().getKey())) continue;
            Point2D a = groupNodePositions.get(e.getSource().getKey());
            Point2D b = groupNodePositions.get(e.getTarget().getKey());
            if (a == null || b == null) continue;
            g.setStroke(Color.web("#46536b", 0.42));
            g.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
        boolean dense = group.nodes().size() > 8;
        for (int i = 0; i < group.nodes().size(); i++) {
            GraphNode n = group.nodes().get(i);
            Point2D at = groupNodePositions.get(n.getKey());
            if (at == null) continue;
            boolean hot = selectedKeys.contains(n.getKey()) || n.getKey().equals(hoverKey);
            double nr = Math.max(8, Math.min(14, 8 + n.getEntity().getRiskScore() / 20));
            Color type = Color.web(n.getEntity().getType().color());
            g.setFill(Color.color(type.getRed(), type.getGreen(), type.getBlue(), 0.9));
            g.fillOval(at.getX() - nr, at.getY() - nr, 2 * nr, 2 * nr);
            g.setStroke(hot ? Color.WHITE : BG);
            g.setLineWidth(hot ? 2 : 1);
            g.strokeOval(at.getX() - nr, at.getY() - nr, 2 * nr, 2 * nr);
            Icons.drawEntity(g, n.getEntity().getType(), at.getX(), at.getY(), nr * 1.35, Color.WHITE);
            if (!dense || i < 8 || hot || isSearchMatch(n)) {
                g.setFill(hot ? Color.WHITE : TEXT);
                g.setFont(Font.font("System", hot ? FontWeight.BOLD : FontWeight.NORMAL, 9.5));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(clip(n.getEntity().getLabel(), 22), at.getX(), at.getY() + nr + 12);
                g.setTextAlign(TextAlignment.LEFT);
            }
        }
        g.setFill(MUTED);
        g.setFont(Font.font("System", 9));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("Clic en el círculo para plegar · doble clic en una entidad para enfocar",
                c.getX(), c.getY() + r + 16);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void arrangeGroupNodes(Group group, Point2D center, double radius) {
        List<GraphNode> nodes = group.nodes();
        if (nodes.isEmpty()) return;
        if (nodes.size() <= 8) {
            double step = Math.min(48, (2 * radius - 60) / Math.max(1, nodes.size() - 1));
            double start = center.getY() - (nodes.size() - 1) * step / 2;
            for (int i = 0; i < nodes.size(); i++) {
                groupNodePositions.put(nodes.get(i).getKey(),
                        new Point2D(center.getX(), start + i * step));
            }
            return;
        }
        groupNodePositions.put(nodes.get(0).getKey(), center);
        double inner = Math.max(20, radius - 30);
        int rings = 0, capacity = 1;
        while (capacity < nodes.size()) {
            rings++;
            capacity += 6 * rings;
        }
        int index = 1;
        for (int ring = 1; ring <= rings && index < nodes.size(); ring++) {
            int slots = Math.min(6 * ring, nodes.size() - index);
            double ringRadius = inner * ring / Math.max(1, rings);
            for (int s = 0; s < slots; s++) {
                double angle = -Math.PI / 2 + 2 * Math.PI * s / slots
                        + (ring % 2 == 0 ? Math.PI / slots : 0);
                GraphNode n = nodes.get(index++);
                groupNodePositions.put(n.getKey(), new Point2D(
                        center.getX() + Math.cos(angle) * ringRadius,
                        center.getY() + Math.sin(angle) * ringRadius));
            }
        }
    }

    private String groupAt(double sx, double sy) {
        for (var entry : groupCenters.entrySet()) {
            Point2D c = entry.getValue();
            double r = groupRadii.getOrDefault(entry.getKey(), 20.0);
            if (Math.hypot(sx - c.getX(), sy - c.getY()) <= r + 8) return entry.getKey();
        }
        return null;
    }

    private Group findGroup(String key) {
        for (Group g : lastGroups) if (g.key().equals(key)) return g;
        return null;
    }

    private GraphEdge edgeAt(double sx, double sy) {
        GraphEdge best = null;
        double bestD = 7;
        for (GraphEdge e : graph.getEdges()) {
            Point2D a = toScreen(e.getSource().getX(), e.getSource().getY());
            Point2D b = toScreen(e.getTarget().getX(), e.getTarget().getY());
            double d = distanceToSegment(sx, sy, a.getX(), a.getY(), b.getX(), b.getY());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len2));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private String edgeKey(GraphEdge e) {
        return e.getSource().getKey() + "|" + e.getTarget().getKey() + "|" + e.getLabel();
    }

    private boolean isSearchMatch(GraphNode n) {
        if (searchHighlight.isBlank()) return false;
        String needle = searchHighlight.toLowerCase();
        return n.getEntity().getLabel().toLowerCase().contains(needle)
                || n.getEntity().getValue().toLowerCase().contains(needle)
                || n.getEntity().getType().label().toLowerCase().contains(needle);
    }

    private void drawOverlays(GraphicsContext g, double w, double h) {
        backW = 0;
        double y = 14;
        if (grouped) {
            String text = lastExpanded != null
                    ? "Grupo " + lastExpanded.title() + " expandido · clic fuera para plegar · Esc para volver"
                    : "Agrupado por " + groupAxis.label() + " · " + lastGroups.size()
                            + " grupos · clic para expandir/plegar · Esc para salir";
            drawPill(g, 14, y, text, TEXT);
            y += 30;
        }
        if (egoFocus && egoRoot != null) {
            GraphNode root = graph.node(egoRoot).orElse(null);
            String label = root == null ? egoRoot : clip(root.getEntity().getLabel(), 28);
            drawPill(g, 14, y, "Foco N1 · " + label + " · " + visibleNodes().size() + " entidades", ACCENT);
            backX = 14;
            backY = y + 26;
            backW = 118;
            backH = 22;
            g.setFill(Color.web("#1f6feb", 0.9));
            g.fillRoundRect(backX, backY, backW, backH, 7, 7);
            g.setFill(Color.WHITE);
            g.setFont(Font.font("System", FontWeight.BOLD, 10.5));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(canGoBack() ? "← Volver (Esc)" : "Salir (Esc)",
                    backX + backW / 2, backY + 15);
            g.setTextAlign(TextAlignment.LEFT);
            y += 56;
        }
        List<String> filters = new ArrayList<>();
        if (focusType != null) filters.add("Tipo: " + focusType.label());
        if (riskFilter > 0) filters.add("Riesgo ≥ " + riskFilter);
        if (tlpFilter.size() < Tlp.values().length) {
            filters.add("TLP: " + tlpFilter.stream().map(Enum::name).sorted()
                    .reduce((a, b) -> a + "/" + b).orElse(""));
        }
        if (!filters.isEmpty()) {
            drawPill(g, 14, y, "Filtro · " + String.join(" · ", filters)
                    + " · " + visibleNodes().size() + " visibles", Color.web("#d29922"));
            y += 30;
        }
        if (!searchHighlight.isBlank()) {
            long matches = visibleNodes().stream().filter(this::isSearchMatch).count();
            drawPill(g, 14, y, "Búsqueda: " + clip(searchHighlight, 24) + " · " + matches + " coincidencias",
                    Color.web("#39c5cf"));
        }
        if (selectedKeys.size() > 1) {
            drawPill(g, 14, y + 30, selectedKeys.size()
                    + " entidades seleccionadas · Supr eliminar · Esc limpiar", ACCENT);
        }
        drawLegend(g, w, h);
    }

    private void drawLegend(GraphicsContext g, double w, double h) {
        if (!legendVisible) return;
        List<GraphNode> visible = visibleNodes();
        if (visible.isEmpty()) return;
        if (grouped) {
            double panelW = 158;
            double panelH = 83;
            double x = 14;
            double y = Math.max(48, h - panelH - 14);
            g.setFill(PANEL);
            g.fillRoundRect(x, y, panelW, panelH, 9, 9);
            g.setStroke(BORDER);
            g.setLineWidth(1);
            g.strokeRoundRect(x, y, panelW, panelH, 9, 9);
            g.setFill(TEXT);
            g.setFont(Font.font("System", FontWeight.BOLD, 10.5));
            g.fillText("Riesgo", x + 12, y + 17);
            double rowY = y + 34;
            for (RiskScore.Level level : List.of(RiskScore.Level.CRITICAL, RiskScore.Level.HIGH,
                    RiskScore.Level.MEDIUM)) {
                g.setFill(Color.web(level.color()));
                g.fillOval(x + 12, rowY - 7, 7, 7);
                g.setFill(TEXT);
                g.setFont(Font.font("System", 9));
                g.fillText(riskRange(level), x + 26, rowY);
                rowY += 15;
            }
            return;
        }
        Map<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
        for (GraphNode n : visible) counts.merge(n.getEntity().getType(), 1, Integer::sum);
        List<Map.Entry<EntityType, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<EntityType, Integer>comparingByValue().reversed());
        int maxRows = h < 600 ? 2 : 7;
        int typeRows = Math.min(maxRows, entries.size());
        double panelH = 30 + typeRows * 16 + 10 + 3 * 15 + 6
                + (entries.size() > typeRows ? 14 : 0);
        double panelW = 204;
        double x = 14;
        double y = Math.max(48, h - panelH - 14);
        g.setFill(PANEL);
        g.fillRoundRect(x, y, panelW, panelH, 9, 9);
        g.setStroke(BORDER);
        g.setLineWidth(1);
        g.strokeRoundRect(x, y, panelW, panelH, 9, 9);
        g.setFill(TEXT);
        g.setFont(Font.font("System", FontWeight.BOLD, 10.5));
        g.fillText("Leyenda", x + 12, y + 18);
        double rowY = y + 33;
        for (int i = 0; i < typeRows; i++) {
            var entry = entries.get(i);
            Color c = Color.web(entry.getKey().color());
            g.setFill(c);
            g.fillOval(x + 12, rowY - 7, 8, 8);
            g.setFill(TEXT);
            g.setFont(Font.font("System", 9.5));
            g.fillText(clip(entry.getKey().label(), 22), x + 26, rowY);
            g.setFill(MUTED);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(String.valueOf(entry.getValue()), x + panelW - 12, rowY);
            g.setTextAlign(TextAlignment.LEFT);
            rowY += 16;
        }
        if (entries.size() > typeRows) {
            g.setFill(MUTED);
            g.setFont(Font.font("System", 9));
            g.fillText("+" + (entries.size() - typeRows) + " tipos", x + 26, rowY);
            rowY += 14;
        }
        rowY += 7;
        g.setStroke(BORDER);
        g.strokeLine(x + 12, rowY - 8, x + panelW - 12, rowY - 8);
        g.setFill(MUTED);
        g.setFont(Font.font("System", 9));
        g.fillText("Riesgo", x + 12, rowY + 2);
        rowY += 15;
        for (RiskScore.Level level : List.of(RiskScore.Level.CRITICAL, RiskScore.Level.HIGH,
                RiskScore.Level.MEDIUM)) {
            g.setFill(Color.web(level.color()));
            g.fillOval(x + 12, rowY - 7, 7, 7);
            g.setFill(TEXT);
            g.setFont(Font.font("System", 9));
            g.fillText(riskRange(level), x + 26, rowY);
            rowY += 15;
        }
    }

    private void drawPill(GraphicsContext g, double x, double y, String text, Color color) {
        Font font = Font.font("System", 10.5);
        double available = getWidth() - x - 14;
        String shown = text;
        double tw = textWidth(shown, font);
        while (tw > available - 20 && shown.length() > 8) {
            shown = shown.substring(0, shown.length() - 2);
            tw = textWidth(shown + "…", font);
        }
        if (!shown.equals(text)) shown = shown + "…";
        double pw = Math.min(available, tw + 24);
        g.setFill(PANEL);
        g.fillRoundRect(x, y, pw, 23, 8, 8);
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5));
        g.setLineWidth(1);
        g.strokeRoundRect(x, y, pw, 23, 8, 8);
        g.setFill(color);
        g.setFont(font);
        g.fillText(shown, x + 12, y + 15);
    }

    private void drawBadge(GraphicsContext g, String text, double x, double y, Color color, double size) {
        Font font = Font.font("System", FontWeight.BOLD, size);
        double tw = textWidth(text, font);
        double w = tw + 10, h = size + 6;
        g.setFill(Color.web("#0d1117", 0.92));
        g.fillRoundRect(x - w / 2, y - h / 2, w, h, 5, 5);
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.65));
        g.setLineWidth(1);
        g.strokeRoundRect(x - w / 2, y - h / 2, w, h, 5, 5);
        g.setFill(color);
        g.setFont(font);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(text, x, y + size / 3);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private double textWidth(String text, Font font) {
        Text t = new Text(text);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    private boolean intersects(Rectangle2D a, List<Rectangle2D> boxes) {
        for (Rectangle2D b : boxes) if (a.intersects(b)) return true;
        return false;
    }

    private RiskScore.Level levelOf(double risk) {
        if (risk >= 80) return RiskScore.Level.CRITICAL;
        if (risk >= 60) return RiskScore.Level.HIGH;
        if (risk >= 35) return RiskScore.Level.MEDIUM;
        return RiskScore.Level.LOW;
    }

    private String riskRange(RiskScore.Level level) {
        return switch (level) {
            case CRITICAL -> "≥ 80 Crítico";
            case HIGH -> "60-79 Alto";
            case MEDIUM -> "35-59 Medio";
            case LOW -> "< 35 Bajo";
        };
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public double getScale() { return scale; }

    public LayoutType getCurrentLayout() { return currentLayout; }

    private record Group(String key, String title, String subtitle, Color color,
                         List<GraphNode> nodes, double maxRisk, int critical, int high) {
    }

    private record Placed(Group group, Point2D center, double radius) {
    }

    private record LabelBox(String text, Rectangle2D box, Font font, Color color, boolean strong) {
    }

    private static final class Link {
        final String a;
        final String b;
        int ab;
        int ba;

        Link(String a, String b) {
            this.a = a;
            this.b = b;
        }
    }
}
