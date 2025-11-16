package dev.badgersnacks.looteditor.ui;

import dev.badgersnacks.looteditor.model.EnchantmentDescriptor;
import dev.badgersnacks.looteditor.model.EnchantmentPool;
import dev.badgersnacks.looteditor.model.EnchantmentPoolEntry;
import dev.badgersnacks.looteditor.services.EnchantmentPoolService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Editor surface for creating and maintaining reusable enchantment pools.
 */
public class EnchantmentPoolPane extends BorderPane {

    private final EnchantmentPoolService poolService;
    private final ObservableList<EnchantmentPool> pools = FXCollections.observableArrayList();
    private final FilteredList<EnchantmentPool> filteredPools = new FilteredList<>(pools);
    private final ObservableList<EnchantmentPoolEntry> poolEntries = FXCollections.observableArrayList();
    private final ListView<EnchantmentPool> poolList = new ListView<>(filteredPools);
    private final TableView<EnchantmentPoolEntry> entryTable = new TableView<>(poolEntries);
    private final TextField namespaceField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField displayNameField = new TextField();
    private final CheckBox treasureCheck = new CheckBox("Allow treasure enchantments");
    private final Button saveButton = new Button("Save Pool");
    private final Button deleteButton = new Button("Delete Pool");
    private final Button addEntryButton = new Button("Add Enchantment");
    private final Button removeEntryButton = new Button("Remove Enchantment");
    private final Button attachButton = new Button("Attach To Selection");
    private final CheckBox showEmptyCheck = new CheckBox("Show empty pools");
    private final EnchantmentPalettePane enchantmentPalettePane = new EnchantmentPalettePane();
    private final HBox dirtyBanner = new HBox(8);

    private Path modpackRoot;
    private Consumer<String> attachHandler = id -> { };
    private Runnable poolsChangedCallback = () -> { };
    private boolean dirty;
    private boolean suppressDirtyEvents;
    private PoolFormState baselineState;

    public EnchantmentPoolPane(EnchantmentPoolService service) {
        this.poolService = Objects.requireNonNull(service, "service");
        buildUi();
        wireActions();
        enchantmentPalettePane.setInsertHandler(descriptor -> {
            poolEntries.add(new EnchantmentPoolEntry(descriptor.id(), 1.0d, 1, 1));
            markDirty();
        });
    }

    public void setModpackRoot(Path root) {
        this.modpackRoot = root;
        reloadPools();
    }

    public void setAttachHandler(Consumer<String> handler) {
        this.attachHandler = handler == null ? id -> { } : handler;
    }

    public void setPoolsChangedCallback(Runnable callback) {
        this.poolsChangedCallback = callback == null ? () -> { } : callback;
    }

    public void setEnchantmentCatalog(List<EnchantmentDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            enchantmentPalettePane.clear();
        } else {
            enchantmentPalettePane.setDescriptors(descriptors);
        }
    }

    private void buildUi() {
        setPadding(new Insets(8));
        showEmptyCheck.setSelected(false);
        showEmptyCheck.selectedProperty().addListener((obs, oldV, newV) -> applyPoolFilter());

        poolList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(EnchantmentPool item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.displayName() + " (" + item.id() + ")");
                }
            }
        });
        poolList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (Objects.equals(oldV, newV)) {
                return;
            }
            EnchantmentPool current = oldV;
            EnchantmentPool target = newV;
            if (current != null && dirty) {
                boolean proceed = confirmPoolSwitch(current);
                if (!proceed) {
                    poolList.getSelectionModel().select(current);
                    return;
                }
            }
            populateForm(target);
        });
        poolList.setPlaceholder(new Label("Create a pool to get started."));
        poolList.setPrefWidth(260);
        VBox poolColumn = new VBox(6, showEmptyCheck, poolList);
        VBox.setVgrow(poolList, Priority.ALWAYS);

        entryTable.setEditable(true);
        entryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<EnchantmentPoolEntry, String> enchantCol = new TableColumn<>("Enchantment");
        enchantCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().enchantmentId()));
        enchantCol.setCellFactory(TextFieldTableCell.forTableColumn());
        enchantCol.setOnEditCommit(evt -> updateEntry(evt.getTablePosition().getRow(),
                e -> new EnchantmentPoolEntry(evt.getNewValue(), e.weight(), e.minLevel(), e.maxLevel())));

        TableColumn<EnchantmentPoolEntry, Double> weightCol = new TableColumn<>("Weight");
        weightCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().weight()).asObject());
        weightCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        weightCol.setOnEditCommit(evt -> updateEntry(evt.getTablePosition().getRow(),
                e -> new EnchantmentPoolEntry(e.enchantmentId(), evt.getNewValue().doubleValue(), e.minLevel(), e.maxLevel())));

        TableColumn<EnchantmentPoolEntry, Integer> minCol = new TableColumn<>("Min Lv");
        minCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().minLevel()).asObject());
        minCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        minCol.setOnEditCommit(evt -> updateEntry(evt.getTablePosition().getRow(),
                e -> e.withLevels(evt.getNewValue().intValue(), e.maxLevel())));

        TableColumn<EnchantmentPoolEntry, Integer> maxCol = new TableColumn<>("Max Lv");
        maxCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().maxLevel()).asObject());
        maxCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        maxCol.setOnEditCommit(evt -> updateEntry(evt.getTablePosition().getRow(),
                e -> e.withLevels(e.minLevel(), evt.getNewValue().intValue())));

        entryTable.getColumns().addAll(enchantCol, weightCol, minCol, maxCol);
        entryTable.setPlaceholder(new Label("Add weighted enchantments here."));

        GridPane form = new GridPane();
        form.setHgap(6);
        form.setVgap(6);
        form.addRow(0, new Label("Namespace"), namespaceField);
        form.addRow(1, new Label("Name"), nameField);
        form.addRow(2, new Label("Display Name"), displayNameField);
        form.add(treasureCheck, 0, 3, 2, 1);
        GridPane.setMargin(treasureCheck, new Insets(4, 0, 0, 0));
        GridPane.setHgrow(namespaceField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(displayNameField, Priority.ALWAYS);

        HBox entryButtons = new HBox(6, addEntryButton, removeEntryButton, attachButton);
        entryButtons.setPadding(new Insets(6, 0, 6, 0));

        dirtyBanner.setAlignment(Pos.CENTER_LEFT);
        dirtyBanner.setStyle("-fx-background-color: #fff4ce; -fx-border-color: #ffd666; -fx-border-radius: 4; "
                + "-fx-background-radius: 4; -fx-padding: 8;");
        Label dirtyLabel = new Label("Unsaved pool changes");
        Button inlineSaveButton = new Button("Save Now");
        inlineSaveButton.setOnAction(e -> saveActivePool());
        Button inlineDiscardButton = new Button("Discard Changes");
        inlineDiscardButton.setOnAction(e -> discardPoolChanges());
        dirtyBanner.getChildren().addAll(dirtyLabel, inlineSaveButton, inlineDiscardButton);
        dirtyBanner.setVisible(false);
        dirtyBanner.setManaged(false);

        VBox editor = new VBox(10, dirtyBanner, form, entryButtons, entryTable, enchantmentPalettePane);
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        VBox.setVgrow(enchantmentPalettePane, Priority.ALWAYS);

        ToolBar toolbar = new ToolBar();
        Button newButton = new Button("New Pool");
        saveButton.setDisable(true);
        deleteButton.setDisable(true);
        attachButton.setDisable(true);
        toolbar.getItems().addAll(newButton, saveButton, deleteButton);

        newButton.setOnAction(e -> createPool());
        saveButton.setOnAction(e -> saveActivePool());
        deleteButton.setOnAction(e -> deleteActivePool());
        addEntryButton.setOnAction(e -> {
            poolEntries.add(new EnchantmentPoolEntry("minecraft:unbreaking", 1.0d, 1, 1));
            markDirty();
        });
        removeEntryButton.setOnAction(e -> {
            EnchantmentPoolEntry selected = entryTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                poolEntries.remove(selected);
                markDirty();
            }
        });
        attachButton.setOnAction(e -> {
            EnchantmentPool selected = poolList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                attachHandler.accept(selected.id());
            }
        });

        HBox content = new HBox(12, poolColumn, editor);
        HBox.setHgrow(editor, Priority.ALWAYS);

        setTop(toolbar);
        setCenter(content);
    }

    private void wireActions() {
        namespaceField.textProperty().addListener((obs, oldV, newV) -> {
            if (!suppressDirtyEvents) {
                markDirty();
            }
        });
        nameField.textProperty().addListener((obs, oldV, newV) -> {
            if (!suppressDirtyEvents) {
                markDirty();
            }
        });
        displayNameField.textProperty().addListener((obs, oldV, newV) -> {
            if (!suppressDirtyEvents) {
                markDirty();
            }
        });
        treasureCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (!suppressDirtyEvents) {
                markDirty();
            }
        });
    }

    private void reloadPools() {
        pools.clear();
        poolEntries.clear();
        markClean();
        if (modpackRoot == null) {
            return;
        }
        try {
            pools.addAll(poolService.listPools(modpackRoot));
        } catch (IOException e) {
            showError("Unable to read enchantment pools", e);
        }
        applyPoolFilter();
        if (!pools.isEmpty()) {
            poolList.getSelectionModel().selectFirst();
        }
    }

    private void populateForm(EnchantmentPool pool) {
        runWithoutDirty(() -> {
            if (pool == null) {
                namespaceField.clear();
                nameField.clear();
                displayNameField.clear();
                treasureCheck.setSelected(false);
                poolEntries.clear();
                deleteButton.setDisable(true);
                attachButton.setDisable(true);
                return;
            }
            namespaceField.setText(pool.namespace());
            nameField.setText(pool.name());
            displayNameField.setText(pool.displayName());
            treasureCheck.setSelected(pool.treasureAllowed());
            poolEntries.setAll(pool.entries());
            deleteButton.setDisable(false);
            attachButton.setDisable(false);
        });
        markClean();
    }

    private void createPool() {
        if (modpackRoot == null) {
            return;
        }
        runWithoutDirty(() -> {
            poolList.getSelectionModel().clearSelection();
            String name = "pool_" + System.currentTimeMillis();
            namespaceField.setText(EnchantmentPoolService.defaultNamespace());
            nameField.setText(name);
            displayNameField.setText("New Enchantment Pool");
            treasureCheck.setSelected(false);
            poolEntries.setAll(new EnchantmentPoolEntry("minecraft:unbreaking", 1.0d, 1, 1));
            deleteButton.setDisable(true);
            attachButton.setDisable(true);
        });
        baselineState = snapshotCurrentForm();
        markDirty();
    }

    private void saveActivePool() {
        if (modpackRoot == null) {
            return;
        }
        try {
            String namespace = namespaceField.getText().isBlank()
                    ? EnchantmentPoolService.defaultNamespace()
                    : namespaceField.getText().trim().toLowerCase(Locale.ROOT);
            String name = EnchantmentPoolService.sanitizeName(nameField.getText());
            String displayName = displayNameField.getText();
            boolean treasure = treasureCheck.isSelected();
            List<EnchantmentPoolEntry> entries = new ArrayList<>(poolEntries);
            if (entries.isEmpty()) {
                showError("Add at least one enchantment entry before saving.", null);
                return;
            }
            EnchantmentPool pool = new EnchantmentPool(namespace, name, displayName, treasure, entries);
            poolService.savePool(modpackRoot, pool);
            reloadPools();
            poolsChangedCallback.run();
            poolList.getSelectionModel().select(pool);
        } catch (Exception e) {
            showError("Unable to save enchantment pool", e);
        }
    }

    private void deleteActivePool() {
        EnchantmentPool selected = poolList.getSelectionModel().getSelectedItem();
        if (selected == null || modpackRoot == null) {
            return;
        }
        try {
            poolService.deletePool(modpackRoot, selected.id());
            reloadPools();
            poolsChangedCallback.run();
        } catch (IOException e) {
            showError("Unable to delete pool", e);
        }
    }

    private void updateEntry(int row, java.util.function.Function<EnchantmentPoolEntry, EnchantmentPoolEntry> mapper) {
        if (row < 0 || row >= poolEntries.size()) {
            return;
        }
        EnchantmentPoolEntry updated = mapper.apply(poolEntries.get(row));
        poolEntries.set(row, updated);
        markDirty();
    }

    private void markDirty() {
        dirty = true;
        if (modpackRoot != null) {
            saveButton.setDisable(false);
        } else {
            saveButton.setDisable(true);
        }
        updateDirtyBanner();
    }

    private void markClean() {
        dirty = false;
        baselineState = snapshotCurrentForm();
        saveButton.setDisable(true);
        updateDirtyBanner();
    }

    private void applyPoolFilter() {
        boolean showEmpty = showEmptyCheck.isSelected();
        filteredPools.setPredicate(pool -> showEmpty || !pool.entries().isEmpty());
    }

    private void discardPoolChanges() {
        if (!dirty) {
            return;
        }
        if (baselineState == null) {
            runWithoutDirty(() -> {
                namespaceField.clear();
                nameField.clear();
                displayNameField.clear();
                treasureCheck.setSelected(false);
                poolEntries.clear();
            });
        } else {
            applySnapshot(baselineState);
        }
        markClean();
    }

    private PoolFormState snapshotCurrentForm() {
        return new PoolFormState(
                namespaceField.getText(),
                nameField.getText(),
                displayNameField.getText(),
                treasureCheck.isSelected(),
                new ArrayList<>(poolEntries)
        );
    }

    private void applySnapshot(PoolFormState state) {
        runWithoutDirty(() -> {
            poolEntries.clear();
            if (state == null) {
                namespaceField.clear();
                nameField.clear();
                displayNameField.clear();
                treasureCheck.setSelected(false);
            } else {
                namespaceField.setText(state.namespace());
                nameField.setText(state.name());
                displayNameField.setText(state.displayName());
                treasureCheck.setSelected(state.treasureAllowed());
                poolEntries.setAll(state.entries());
            }
        });
    }

    private void runWithoutDirty(Runnable action) {
        boolean previous = suppressDirtyEvents;
        suppressDirtyEvents = true;
        try {
            action.run();
        } finally {
            suppressDirtyEvents = previous;
        }
    }

    private void updateDirtyBanner() {
        boolean visible = dirty;
        dirtyBanner.setVisible(visible);
        dirtyBanner.setManaged(visible);
    }

    private void showError(String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Enchantment Pools");
        alert.setHeaderText(message);
        if (exception != null) {
            alert.setContentText(exception.getMessage());
        }
        alert.show();
    }

    private boolean confirmPoolSwitch(EnchantmentPool activePool) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved pool edits");
        alert.setHeaderText("Enchantment pool \"" + activePool.displayName() + "\" has unsaved changes.");
        alert.setContentText("Switching pools will discard your edits. Continue?");
        alert.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        return alert.showAndWait().map(button -> button == ButtonType.OK).orElse(false);
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    private record PoolFormState(
            String namespace,
            String name,
            String displayName,
            boolean treasureAllowed,
            List<EnchantmentPoolEntry> entries
    ) { }

    private static class DoubleStringConverter extends StringConverter<Double> {
        @Override
        public String toString(Double object) {
            return object == null ? "" : Double.toString(object);
        }

        @Override
        public Double fromString(String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                return 1.0d;
            }
        }
    }

    private static class IntegerStringConverter extends StringConverter<Integer> {
        @Override
        public String toString(Integer object) {
            return object == null ? "" : Integer.toString(object);
        }

        @Override
        public Integer fromString(String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }
}
