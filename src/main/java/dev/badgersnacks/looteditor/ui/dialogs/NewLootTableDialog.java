package dev.badgersnacks.looteditor.ui.dialogs;

import dev.badgersnacks.looteditor.services.LootTableService.LootTableTemplate;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * Dialog used to gather namespace/path/template when creating a loot table.
 */
public class NewLootTableDialog extends Dialog<NewLootTableRequest> {

    private final TextField namespaceField = new TextField("minecraft");
    private final TextField pathField = new TextField("chests/new_table");
    private final ComboBox<LootTableTemplate> templateBox = new ComboBox<>();
    private final Label previewLabel = new Label();

    public NewLootTableDialog(Window owner) {
        setTitle("New Loot Table");
        setHeaderText("Create a loot table inside KubeJS/data");
        if (owner != null) {
            initOwner(owner);
        }

        templateBox.getItems().addAll(LootTableTemplate.values());
        templateBox.getSelectionModel().select(LootTableTemplate.GENERIC_CHEST);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Namespace"), namespaceField);
        grid.addRow(1, new Label("Loot Table ID"), pathField);
        Label hint = new Label("Example: chests/forge/rare_drop (becomes kubejs/data/<namespace>/loot_tables/... .json)");
        hint.getStyleClass().add("dialog-hint");
        grid.add(hint, 0, 2, 2, 1);
        grid.addRow(3, new Label("Template"), templateBox);
        previewLabel.getStyleClass().add("dialog-hint");
        grid.add(previewLabel, 0, 4, 2, 1);

        getDialogPane().setContent(grid);
        ButtonType createType = new ButtonType("Create", ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, createType);

        Node createButton = getDialogPane().lookupButton(createType);
        createButton.disableProperty().bind(
                namespaceField.textProperty().isEmpty()
                        .or(pathField.textProperty().isEmpty())
                        .or(templateBox.valueProperty().isNull()));

        namespaceField.textProperty().addListener((obs, oldV, newV) -> updatePreview());
        pathField.textProperty().addListener((obs, oldV, newV) -> updatePreview());
        updatePreview();

        setResultConverter(button -> {
            if (button != createType) {
                return null;
            }
            String namespace = namespaceField.getText().trim();
            String tablePath = pathField.getText().trim();
            return new NewLootTableRequest(namespace, tablePath, templateBox.getValue());
        });
    }

    private void updatePreview() {
        String ns = namespaceField.getText().trim();
        String tablePath = pathField.getText().trim();
        if (ns.isBlank() || tablePath.isBlank()) {
            previewLabel.setText("");
            return;
        }
        String sanitized = tablePath.endsWith(".json") ? tablePath.substring(0, tablePath.length() - 5) : tablePath;
        previewLabel.setText("Creates: kubejs/data/" + ns + "/loot_tables/" + sanitized + ".json");
    }
}
