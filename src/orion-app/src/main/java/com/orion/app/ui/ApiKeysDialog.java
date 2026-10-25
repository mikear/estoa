package com.orion.app.ui;

import com.orion.sources.ApiKeyManager;
import com.orion.sources.SourceConnector;
import com.orion.sources.SourceRegistry;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Dialogo de gestion de claves de API: estado de cada conector comercial,
 * alta/baja de claves cifradas (AES-256-GCM) y muestra enmascarada.
 */
public class ApiKeysDialog extends Dialog<Void> {

    public ApiKeysDialog(Stage owner, SourceRegistry registry) {
        initOwner(owner);
        setTitle("Gestión de claves API");
        setHeaderText("Claves cifradas con AES-256-GCM en ~/.orion/keys.dat");

        TableView<SourceConnector> table = new TableView<>();
        table.setPlaceholder(new Label("Sin conectores registrados"));
        table.setPrefHeight(320);
        table.setPrefWidth(720);

        TableColumn<SourceConnector, String> nameCol = new TableColumn<>("Servicio");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        nameCol.setPrefWidth(330);
        TableColumn<SourceConnector, String> typeCol = new TableColumn<>("Tipo");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().category() == SourceConnector.Category.COMMERCIAL ? "Comercial" : "Abierto"));
        typeCol.setPrefWidth(90);
        TableColumn<SourceConnector, String> stateCol = new TableColumn<>("Estado");
        stateCol.setCellValueFactory(d -> {
            SourceConnector c = d.getValue();
            if (!c.requiresApiKey()) return new SimpleStringProperty("sin clave requerida");
            return new SimpleStringProperty(
                    effectiveKey(registry, c) == null ? "sin clave" : "conectada");
        });
        stateCol.setPrefWidth(130);
        TableColumn<SourceConnector, String> keyCol = new TableColumn<>("Clave");
        keyCol.setCellValueFactory(d -> {
            String k = effectiveKey(registry, d.getValue());
            return new SimpleStringProperty(k == null ? "-" : ApiKeyManager.mask(k));
        });
        keyCol.setPrefWidth(170);

        table.getColumns().addAll(nameCol, typeCol, stateCol, keyCol);
        table.setItems(FXCollections.observableArrayList(registry.all()));
        table.getSelectionModel().selectFirst();

        Label nameLabel = new Label();
        PasswordField keyField = new PasswordField();
        keyField.setPromptText("Pega aquí la clave del servicio seleccionado");
        keyField.setPrefWidth(420);
        Button save = new Button("Guardar clave");
        save.getStyleClass().add("primary");
        Button delete = new Button("Eliminar");
        Button close = new Button("Cerrar");
        save.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Runnable refresh = () -> {
            table.refresh();
            SourceConnector sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                nameLabel.setText(sel.id() + " (" + sel.apiKeyName() + ")");
            }
        };
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refresh.run());

        save.setOnAction(e -> {
            SourceConnector sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && !keyField.getText().isBlank()) {
                String value = keyField.getText().trim();
                registry.apiKeyManager().setKey(sel.id(), value);
                if ("ABUSECH_KEY".equals(sel.apiKeyName())) {
                    registry.apiKeyManager().setKey("abusech", value);
                }
                keyField.clear();
                refresh.run();
            }
        });
        delete.setOnAction(e -> {
            SourceConnector sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                registry.apiKeyManager().deleteKey(sel.id());
                refresh.run();
            }
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(10, 0, 0, 0));
        form.add(new Label("Servicio:"), 0, 0);
        form.add(nameLabel, 1, 0);
        form.add(new Label("Clave:"), 0, 1);
        form.add(keyField, 1, 1);

        HBox buttons = new HBox(8, save, delete, close);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        VBox box = new VBox(10, table, form, buttons);
        box.setPrefWidth(740);
        getDialogPane().setContent(box);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        close.setOnAction(e -> setResult(null));
        setResultConverter(b -> null);
        refresh.run();
    }

    /** AbuseCH comparte una unica clave gratuita entre sus tres conectores. */
    private static String effectiveKey(SourceRegistry registry, SourceConnector c) {
        String key = registry.apiKeyManager().getKey(c.id());
        if (key == null && "ABUSECH_KEY".equals(c.apiKeyName())) {
            key = registry.apiKeyManager().getKey("abusech");
        }
        return key;
    }
}
