package com.orion.app.ui;

import com.orion.graph.OrionGraph;
import com.orion.intel.AttackMatrixService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Vista de matriz MITRE ATT&amp;CK: columnas por tactica, celdas por tecnica
 * con realce de las observadas en el caso y contador por columna.
 */
public class AttackMatrixView extends StackPane {

    private final VBox content = new VBox(10);
    private final GridPane grid = new GridPane();
    private final ScrollPane scroll = new ScrollPane(grid);

    public AttackMatrixView() {
        getStyleClass().add("matrix-view");
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new javafx.geometry.Insets(14));
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("panel-scroll");
        Label title = new Label("MATRIZ MITRE ATT&CK - técnicas observadas en el caso");
        title.getStyleClass().add("matrix-title");
        Label scope = new Label("Catálogo embebido offline: la cobertura se mide sobre el subconjunto,"
                + " no sobre todas las técnicas de ATT&CK Enterprise.");
        scope.getStyleClass().add("muted");
        scope.setWrapText(true);
        content.getChildren().addAll(title, scope, scroll);
        content.setPadding(new javafx.geometry.Insets(6));
        getChildren().add(content);
    }

    public void update(AttackMatrixService service, OrionGraph g) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        var rows = service.matrix(g);
        if (rows.isEmpty()) {
            Label empty = new Label("Sin datos ATT&CK");
            empty.getStyleClass().add("muted");
            grid.add(empty, 0, 0);
            return;
        }
        int col = 0;
        for (AttackMatrixService.TacticRow tactic : rows) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setMinWidth(148);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);

            VBox header = new VBox(2);
            header.setAlignment(Pos.CENTER);
            header.setPadding(new javafx.geometry.Insets(8, 4, 8, 4));
            header.getStyleClass().add("tactic-header" + (tactic.observedCount() > 0 ? " hit" : ""));
            Label name = new Label(tactic.tacticNameEs());
            name.getStyleClass().add("tactic-name");
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setAlignment(Pos.CENTER);
            Label id = new Label(tactic.tacticId() + " - " + tactic.observedCount());
            id.getStyleClass().add("tactic-id");
            header.getChildren().addAll(name, id);
            grid.add(header, col, 0);

            int row = 1;
            for (AttackMatrixService.TechniqueCell cell : tactic.techniques()) {
                Label cellLabel = new Label(cell.id() + "\n" + cell.name());
                cellLabel.getStyleClass().add("tech-cell" + (cell.observed() ? " observed" : ""));
                cellLabel.setWrapText(true);
                cellLabel.setMaxWidth(Double.MAX_VALUE);
                cellLabel.setMinHeight(44);
                cellLabel.setAlignment(Pos.TOP_LEFT);
                if (cell.observed()) {
                    Tooltip tooltip = new Tooltip(cell.name() + " - observada en el caso");
                    Tooltip.install(cellLabel, tooltip);
                }
                grid.add(cellLabel, col, row++);
            }
            col++;
        }
    }
}
