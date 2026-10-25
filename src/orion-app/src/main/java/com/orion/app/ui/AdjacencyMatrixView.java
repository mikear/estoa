package com.orion.app.ui;

import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjacencyMatrixView extends ScrollPane {

    private static final int MAX_NODES = 80;
    private static final double RIGHT = 28;
    private static final double BOTTOM = 34;

    private final Canvas canvas = new Canvas(600, 400);
    private OrionGraph graph = new OrionGraph();

    public AdjacencyMatrixView() {
        setContent(canvas);
        setFitToHeight(false);
        setFitToWidth(false);
        setPannable(true);
        getStyleClass().add("canvas-pane");
        widthProperty().addListener((o, a, b) -> draw());
        viewportBoundsProperty().addListener((o, a, b) -> draw());
    }

    public void update(OrionGraph graph) {
        this.graph = graph;
        draw();
    }

    private void draw() {
        double viewportW = getViewportBounds().getWidth();
        double viewportH = getViewportBounds().getHeight();
        double availableW = Math.max(360, viewportW > 1 ? viewportW - 2 : getWidth() - 2);
        double availableH = Math.max(260, viewportH > 1 ? viewportH - 2 : getHeight() - 2);

        List<GraphNode> all = new ArrayList<>(graph.getNodes());
        if (all.isEmpty()) {
            sizeCanvas(availableW, availableH);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.web("#0d1117"));
            g.fillRect(0, 0, availableW, availableH);
            g.setFill(Color.web("#8b949e"));
            g.setFont(Font.font("System", 13));
            g.fillText("Sin entidades para representar en la matriz.", 24, 40);
            return;
        }

        all.sort(Comparator.comparingDouble((GraphNode n) -> n.getEntity().getRiskScore()).reversed()
                .thenComparing(n -> n.getEntity().getLabel()));
        List<GraphNode> nodes = all.size() > MAX_NODES ? all.subList(0, MAX_NODES) : all;
        int n = nodes.size();

        Font labelFont = Font.font("System", 9.5);
        int maxChars = n <= 12 ? 30 : n <= 30 ? 26 : 20;
        String[] labels = new String[n];
        double longest = 0;
        for (int i = 0; i < n; i++) {
            labels[i] = clip(nodes.get(i).getEntity().getLabel(), maxChars);
            longest = Math.max(longest, textWidth(labels[i], labelFont));
        }
        double left = Math.max(90, Math.min(220, longest + 26));
        double top = Math.max(78, Math.min(190, 54 + longest * 0.82));
        double cell = MatrixLayout.cell(n, availableW, availableH, left, top, RIGHT, BOTTOM);
        double matrix = n * cell;
        double width = Math.max(availableW, left + matrix + RIGHT);
        double height = Math.max(availableH, top + matrix + BOTTOM);
        sizeCanvas(width, height);

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0d1117"));
        g.fillRect(0, 0, width, height);
        g.setTextAlign(TextAlignment.LEFT);

        g.setFill(Color.web("#c9d1d9"));
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.fillText("Matriz de adyacencia · fila → columna", 16, 22);
        g.setFill(Color.web("#8b949e"));
        g.setFont(Font.font("System", 9.5));
        String scope = "Mostrando " + n + " de " + all.size() + " entidades (mayor riesgo) · "
                + graph.edgeCount() + " relaciones";
        g.fillText(scope, 16, 40);

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(nodes.get(i).getKey(), i);
        int[][] matrixData = new int[n][n];
        for (GraphEdge e : graph.getEdges()) {
            Integer row = index.get(e.getSource().getKey());
            Integer col = index.get(e.getTarget().getKey());
            if (row == null || col == null || row.equals(col)) continue;
            matrixData[row][col]++;
        }

        double matrixX = left + Math.max(0, (width - left - RIGHT - matrix) / 2);
        double matrixY = top + Math.max(0, (height - top - BOTTOM - matrix) / 2);
        double pad = Math.max(1, Math.min(3, cell * 0.06));

        g.setStroke(Color.web("#21262d"));
        g.setLineWidth(1);
        for (int i = 0; i <= n; i++) {
            g.strokeLine(matrixX + i * cell, matrixY, matrixX + i * cell, matrixY + matrix);
            g.strokeLine(matrixX, matrixY + i * cell, matrixX + matrix, matrixY + i * cell);
        }

        for (int row = 0; row < n; row++) {
            GraphNode source = nodes.get(row);
            Color color = Color.web(source.getEntity().getType().color());
            for (int col = 0; col < n; col++) {
                double x = matrixX + col * cell;
                double y = matrixY + row * cell;
                if (row == col) {
                    g.setFill(Color.web("#30363d"));
                    g.fillRect(x + pad, y + pad, cell - pad * 2, cell - pad * 2);
                    continue;
                }
                int count = matrixData[row][col];
                if (count == 0) continue;
                g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                        Math.min(0.95, 0.38 + count * 0.16)));
                g.fillRect(x + pad, y + pad, cell - pad * 2, cell - pad * 2);
                if (count > 1 && cell >= 12) {
                    g.setFill(Color.WHITE);
                    g.setFont(Font.font("System", Math.max(7, Math.min(16, cell * 0.32))));
                    g.setTextAlign(TextAlignment.CENTER);
                    g.fillText(String.valueOf(count), x + cell / 2, y + cell / 2 + 3);
                    g.setTextAlign(TextAlignment.LEFT);
                }
            }
        }

        double labelSize = Math.max(8.5, Math.min(12, cell * 0.22 + 6.5));
        Font rowFont = Font.font("System", labelSize);
        for (int row = 0; row < n; row++) {
            GraphNode source = nodes.get(row);
            double centerY = matrixY + row * cell + cell / 2;
            g.setFill(Color.web("#c9d1d9"));
            g.setFont(rowFont);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(labels[row], matrixX - 8, centerY + labelSize * 0.35);
            g.setFill(Color.web(source.getEntity().getType().color()));
            g.fillOval(matrixX - 6, centerY - 2, 4, 4);
            g.setTextAlign(TextAlignment.LEFT);
        }

        for (int col = 0; col < n; col++) {
            g.save();
            g.translate(matrixX + col * cell + cell / 2, matrixY - 8);
            g.rotate(-55);
            g.setFill(Color.web("#8b949e"));
            g.setFont(rowFont);
            g.fillText(labels[col], 0, 0);
            g.restore();
        }

        g.setFill(Color.web("#8b949e"));
        g.setFont(Font.font("System", 9));
        g.fillText("Cada celda indica una relación dirigida; el color corresponde al tipo de la entidad de origen.",
                16, matrixY + matrix + 22);
    }

    private void sizeCanvas(double width, double height) {
        if (canvas.getWidth() != width || canvas.getHeight() != height) {
            canvas.setWidth(width);
            canvas.setHeight(height);
        }
    }

    private double textWidth(String text, Font font) {
        Text t = new Text(text);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    private String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
