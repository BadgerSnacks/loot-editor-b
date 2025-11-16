package dev.badgersnacks.looteditor.ui;

import com.fasterxml.jackson.databind.JsonNode;
import dev.badgersnacks.looteditor.catalog.ItemCatalog;
import dev.badgersnacks.looteditor.catalog.ItemDescriptor;
import dev.badgersnacks.looteditor.catalog.ItemIconCache;
import dev.badgersnacks.looteditor.model.EnchantmentPool;
import dev.badgersnacks.looteditor.model.EnchantmentPoolLink;
import dev.badgersnacks.looteditor.model.LootPoolEntryModel;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.persistence.EnchantmentPoolLinkService;
import dev.badgersnacks.looteditor.services.EnchantmentPoolAdapter;
import dev.badgersnacks.looteditor.services.EnchantmentPoolAdapter.PathAwarePoolLinkWriter;
import dev.badgersnacks.looteditor.services.EnchantmentPoolAdapter.RebuildResult;
import dev.badgersnacks.looteditor.services.EnchantmentPoolService;
import dev.badgersnacks.looteditor.services.LootTableService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Drag-and-drop editing surface for loot table entries.
 */
public class LootTableEditorPane extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootTableEditorPane.class);

    private final LootTableService lootTableService;
    private final ItemIconCache iconCache;
    private final ListView<LootPoolEntryModel> entryList = new ListView<>();
    private final TextArea jsonPreview = new TextArea();
    private final Button saveButton = new Button("Save Loot Table");
    private final Button revertButton = new Button("Revert Changes");

    private final ObservableList<LootPoolEntryModel> entries = FXCollections.observableArrayList();
    private final Map<String, EnchantmentPool> cachedPools = new HashMap<>();

    private LootTableDescriptor descriptor;
    private ItemCatalog itemCatalog;
    private JsonNode currentNode;
    private JsonNode originalNode;
    private Consumer<JsonNode> saveHandler = node -> { };
    private List<LootPoolEntryModel> baselineEntries = List.of();
    private Path modpackRoot;
    private EnchantmentPoolService enchantmentPoolService;
    private EnchantmentPoolLinkService poolLinkService;

    public LootTableEditorPane(LootTableService lootTableService, ItemIconCache iconCache) {
        this.lootTableService = Objects.requireNonNull(lootTableService, "lootTableService");
        this.iconCache = Objects.requireNonNull(iconCache, "iconCache");
        buildUi();
    }

    public void setOnSave(Consumer<JsonNode> onSave) {
        this.saveHandler = onSave == null ? node -> { } : onSave;
    }

    public void configurePoolContext(Path modpackRoot,
                                     EnchantmentPoolService poolService,
                                     EnchantmentPoolLinkService linkService) {
        // Pools live on disk inside the pack, so we need the modpack root and both services to resolve IDs back
        // to user-friendly names whenever a table is loaded.
        this.modpackRoot = modpackRoot;
        this.enchantmentPoolService = poolService;
        this.poolLinkService = linkService;
        refreshEnchantmentPools();
    }

    public void clear() {
        descriptor = null;
        currentNode = null;
        originalNode = null;
        baselineEntries = List.of();
        entries.clear();
        jsonPreview.clear();
        updateControls();
        entryList.refresh();
    }

    public void refreshEnchantmentPools() {
        if (modpackRoot == null || enchantmentPoolService == null) {
            cachedPools.clear();
            return;
        }
        try {
            cachedPools.clear();
            enchantmentPoolService.listPools(modpackRoot)
                    .forEach(pool -> cachedPools.put(pool.id(), pool));
            entryList.refresh();
        } catch (IOException ignored) {
        }
    }

    public void attachPoolToSelection(String poolId) {
        if (!hasDescriptor() || poolId == null || poolId.isBlank()) {
            return;
        }
        LootPoolEntryModel selected = entryList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        // Pools are tracked via the model, so updating the record automatically refreshes the preview + dirty state.
        replaceSelectedEntry(selected, selected.withEnchantmentPool(poolId));
    }

    public void clearPoolFromSelection() {
        LootPoolEntryModel selected = entryList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        replaceSelectedEntry(selected, selected.withEnchantmentPool(null));
    }

    public void displayDescriptor(LootTableDescriptor descriptor, JsonNode node) {
        this.descriptor = descriptor;
        this.originalNode = node == null ? null : node.deepCopy();
        this.currentNode = node == null ? null : node.deepCopy();
        List<LootPoolEntryModel> extracted;
        if (poolLinkService != null && modpackRoot != null) {
            List<EnchantmentPoolLink> links = poolLinkService.loadLinks(modpackRoot, descriptor);
            extracted = EnchantmentPoolAdapter.mergeForEditing(this.currentNode, links);
        } else {
            extracted = lootTableService.extractEntries(this.currentNode);
        }
        this.baselineEntries = new ArrayList<>(extracted);
        this.entries.setAll(extracted);
        jsonPreview.setText(node == null ? "" : lootTableService.prettyPrint(node));
        updateControls();
        entryList.refresh();
        LOGGER.info("Display descriptor {} entries: {}", descriptor == null ? "n/a" : descriptor.qualifiedName(), entries.size());
    }

    private void buildUi() {
        entryList.setItems(entries);
        entryList.setCellFactory(lv -> new EntryCell());
        entryList.setPlaceholder(new Label("Drop palette items here to build loot entries."));
        VBox entryBox = new VBox(6, new Label("Loot Entries"), entryList);
        VBox.setVgrow(entryList, Priority.ALWAYS);
        setCenter(entryBox);
        configureEntryListInteractions();

        jsonPreview.setPrefRowCount(8);
        jsonPreview.setEditable(false);
        jsonPreview.setStyle("-fx-font-family: 'Consolas', 'JetBrains Mono', monospace;");
        VBox bottomBox = new VBox(new Label("JSON Preview"), jsonPreview);
        VBox.setVgrow(jsonPreview, Priority.ALWAYS);
        setBottom(bottomBox);

        ToolBar toolBar = new ToolBar();
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> triggerSave());
        revertButton.setDisable(true);
        revertButton.setOnAction(e -> revert());
        toolBar.getItems().addAll(saveButton, revertButton);
        setTop(toolBar);
    }

    private void revert() {
        if (descriptor != null && originalNode != null) {
            displayDescriptor(descriptor, originalNode.deepCopy());
        }
    }

    private void configureEntryListInteractions() {
        entryList.setOnDragOver(event -> {
            if (!hasDescriptor()) {
                event.consume();
                return;
            }
            if (event.getGestureSource() != entryList && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        entryList.setOnDragDropped(event -> {
            if (!hasDescriptor()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            if (event.getDragboard().hasString()) {
                LootPoolEntryModel entry = deserialize(event.getDragboard().getString());
                addEntry(entry);
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        entryList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && hasDescriptor()) {
                LootPoolEntryModel selected = entryList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    removeEntry(selected);
                }
            }
        });
    }


    private void triggerSave() {
        if (!isEditableDescriptor()) {
            return;
        }
        try {
            JsonNode updated = rebuildCurrentTable(new EditorLinkWriter());
            this.currentNode = updated;
            this.originalNode = updated.deepCopy();
            this.baselineEntries = new ArrayList<>(entries);
            jsonPreview.setText(lootTableService.prettyPrint(updated));
            saveHandler.accept(updated);
            updateControls();
            entryList.refresh();
        } catch (Exception e) {
            showErrorDialog("Failed to save loot table", e);
        }
    }

    private void refreshPreview() {
        if (descriptor == null) {
            return;
        }
        try {
            JsonNode preview = rebuildCurrentTable(new NoOpLinkWriter());
            jsonPreview.setText(lootTableService.prettyPrint(preview));
        } catch (Exception e) {
            // fall back to basic preview so the UI is still responsive
            JsonNode fallback = lootTableService.rebuildTable(currentNode, new ArrayList<>(entries));
            jsonPreview.setText(lootTableService.prettyPrint(fallback));
        }
    }

    private JsonNode rebuildCurrentTable(PathAwarePoolLinkWriter writer) throws Exception {
        if (poolsEnabled()) {
            RebuildResult rebuilt = EnchantmentPoolAdapter.rebuild(
                    currentNode,
                    new ArrayList<>(entries),
                    descriptor,
                    enchantmentPoolService,
                    writer);
            return rebuilt.node();
        }
        return lootTableService.rebuildTable(currentNode, new ArrayList<>(entries));
    }

    private void removeEntry(LootPoolEntryModel entry) {
        if (!hasDescriptor()) {
            return;
        }
        entries.remove(entry);
        refreshPreview();
        updateControls();
    }

    private boolean hasDescriptor() {
        return descriptor != null;
    }

    private boolean isEditableDescriptor() {
        return descriptor != null;
    }

    private boolean isDirty() {
        if (descriptor == null) {
            return false;
        }
        if (entries.size() != baselineEntries.size()) {
            return true;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).equals(baselineEntries.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exposed so the hosting view can warn the user before closing the application.
     */
    public boolean hasUnsavedChanges() {
        return isDirty();
    }

    private void updateControls() {
        boolean dirty = isDirty();
        saveButton.setDisable(!isEditableDescriptor() || !dirty);
        revertButton.setDisable(!dirty);
    }

    private boolean isEntryModified(int index, LootPoolEntryModel entry) {
        if (entry == null || index < 0) {
            return false;
        }
        if (index >= baselineEntries.size()) {
            return true;
        }
        return !entry.equals(baselineEntries.get(index));
    }

    private boolean poolsEnabled() {
        return modpackRoot != null && enchantmentPoolService != null && poolLinkService != null;
    }

    public void addEntry(LootPoolEntryModel entry) {
        if (!hasDescriptor()) {
            return;
        }
        entries.add(entry);
        refreshPreview();
        updateControls();
        entryList.scrollTo(entries.size() - 1);
    }

    private void replaceSelectedEntry(LootPoolEntryModel original, LootPoolEntryModel updated) {
        if (!hasDescriptor()) {
            return;
        }
        int index = entries.indexOf(original);
        if (index < 0) {
            index = entryList.getSelectionModel().getSelectedIndex();
        }
        replaceEntryAt(index, updated);
    }

    private void replaceEntryAt(int index, LootPoolEntryModel updated) {
        if (!hasDescriptor() || index < 0 || index >= entries.size()) {
            return;
        }
        entries.set(index, updated);
        refreshPreview();
        updateControls();
    }

    public void setItemCatalog(ItemCatalog catalog) {
        this.itemCatalog = catalog;
        entryList.refresh();
    }

    public int entryCount() {
        return entries.size();
    }

    private static LootPoolEntryModel deserialize(String raw) {
        String[] parts = raw.split("\\|", 6);
        String item = parts.length > 0 ? parts[0] : "unknown";
        double weight = 1.0d;
        if (parts.length > 1) {
            try {
                weight = Double.parseDouble(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        String type = parts.length > 2 ? parts[2] : "minecraft:item";
        int min = 1;
        int max = 1;
        if (parts.length > 3) {
            try {
                min = Math.max(1, Integer.parseInt(parts[3]));
            } catch (NumberFormatException ignored) {
            }
        }
        if (parts.length > 4) {
            try {
                max = Math.max(min, Integer.parseInt(parts[4]));
            } catch (NumberFormatException ignored) {
            }
        }
        String pool = parts.length > 5 && !parts[5].isBlank() ? parts[5] : null;
        return new LootPoolEntryModel(item, weight, type, min, max, pool);
    }

    private String resolveDisplayName(String qualifiedId) {
        if (itemCatalog == null) {
            return qualifiedId;
        }
        return itemCatalog.find(qualifiedId)
                .map(ItemDescriptor::displayName)
                .orElse(qualifiedId);
    }

    private String resolvePoolLabel(String poolId) {
        if (poolId == null) {
            return "";
        }
        EnchantmentPool pool = cachedPools.get(poolId);
        if (pool == null) {
            return poolId;
        }
        return pool.displayName() + " (" + pool.id() + ")";
    }

    private void showErrorDialog(String message, Exception throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Loot Editor B");
        alert.setHeaderText(message);
        if (throwable != null) {
            alert.setContentText(throwable.getMessage());
        }
        alert.show();
    }

    private class EditorLinkWriter implements PathAwarePoolLinkWriter {
        @Override
        public Path modpackRoot() {
            return modpackRoot;
        }

        @Override
        public void writeLinks(LootTableDescriptor descriptor, List<EnchantmentPoolLink> links) throws Exception {
            poolLinkService.saveLinks(modpackRoot, descriptor, links);
        }
    }

    private class NoOpLinkWriter implements PathAwarePoolLinkWriter {
        @Override
        public Path modpackRoot() {
            return modpackRoot;
        }

        @Override
        public void writeLinks(LootTableDescriptor descriptor, List<EnchantmentPoolLink> links) {
            // preview-only; do not persist
        }
    }

    private class EntryCell extends ListCell<LootPoolEntryModel> {
        private final ImageView iconView = new ImageView();
        private final Label titleLabel = new Label();
        private final Label badgeLabel = new Label();
        private final Label poolLabel = new Label();
        private final Label subtitleLabel = new Label();
        private final Spinner<Double> weightSpinner = new Spinner<>();
        private final Spinner<Integer> minSpinner = new Spinner<>();
        private final Spinner<Integer> maxSpinner = new Spinner<>();
        private final Label weightLabel = new Label("W:");
        private final Label minLabel = new Label("Min:");
        private final Label maxLabel = new Label("Max:");
        private final HBox controlBox = new HBox(6, weightLabel, weightSpinner, minLabel, minSpinner, maxLabel, maxSpinner);
        private final Button removeButton = new Button("Remove");
        private final Button detachPoolButton = new Button("Clear Pool");
        private final HBox headerRow = new HBox(6, titleLabel, badgeLabel);
        private final VBox textBox = new VBox(4, headerRow, poolLabel, subtitleLabel, controlBox);
        private final VBox buttonBox = new VBox(6, removeButton, detachPoolButton);
        private final HBox container = new HBox(8, iconView, textBox, buttonBox);
        private LootPoolEntryModel currentEntry;
        private boolean updating;

        EntryCell() {
            iconView.setFitWidth(24);
            iconView.setFitHeight(24);
            subtitleLabel.getStyleClass().add("entry-meta");
            badgeLabel.setTextFill(Color.web("#b65c00"));
            badgeLabel.setStyle("-fx-background-color: rgba(255, 193, 94, 0.45); -fx-padding: 1 6 1 6; -fx-background-radius: 8;");
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);
            poolLabel.getStyleClass().add("pool-meta");
            poolLabel.setVisible(false);
            poolLabel.setManaged(false);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            controlBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            buttonBox.setAlignment(Pos.CENTER_LEFT);
            removeButton.setOnAction(e -> {
                if (currentEntry != null) {
                    removeEntry(currentEntry);
                }
            });
            detachPoolButton.setOnAction(e -> {
                if (currentEntry != null) {
                    entryList.getSelectionModel().select(currentEntry);
                    replaceSelectedEntry(currentEntry, currentEntry.withEnchantmentPool(null));
                }
            });

            weightSpinner.setPrefWidth(80);
            weightSpinner.setEditable(true);
            weightSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 100.0, 1.0, 0.1));
            minSpinner.setPrefWidth(70);
            minSpinner.setEditable(true);
            minSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
            maxSpinner.setPrefWidth(70);
            maxSpinner.setEditable(true);
            maxSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));

            weightSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                if (!hasDescriptor() || currentEntry == null || updating || newV == null || oldV == null
                        || newV.doubleValue() == oldV.doubleValue()) {
                    return;
                }
                applyUpdate(currentEntry.withWeight(newV));
            });

            minSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                if (!hasDescriptor() || currentEntry == null || updating || newV == null) {
                    return;
                }
                if (newV > maxSpinner.getValue()) {
                    updating = true;
                    maxSpinner.getValueFactory().setValue(newV);
                    updating = false;
                }
                applyUpdate(currentEntry.withCounts(newV, maxSpinner.getValue()));
            });

            maxSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                if (!hasDescriptor() || currentEntry == null || updating || newV == null) {
                    return;
                }
                if (newV < minSpinner.getValue()) {
                    updating = true;
                    minSpinner.getValueFactory().setValue(newV);
                    updating = false;
                }
                applyUpdate(currentEntry.withCounts(minSpinner.getValue(), newV));
            });
        }

        @Override
        protected void updateItem(LootPoolEntryModel item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                currentEntry = null;
                return;
            }
            updating = true;
            currentEntry = item;
            iconView.setImage(iconCache.imageFor(item.itemId(), itemCatalog));
            titleLabel.setText(resolveDisplayName(item.itemId()));
            subtitleLabel.setText(String.format("weight %.2f, count %d-%d", item.weight(), item.minCount(), item.maxCount()));
            weightSpinner.getValueFactory().setValue(item.weight());
            minSpinner.getValueFactory().setValue(item.minCount());
            maxSpinner.getValueFactory().setValue(item.maxCount());
            boolean enableEditing = hasDescriptor();
            controlBox.setDisable(!enableEditing);
            removeButton.setDisable(!enableEditing);
            String poolId = item.enchantmentPoolId();
            if (poolId != null) {
                poolLabel.setText(resolvePoolLabel(poolId));
                poolLabel.setVisible(true);
                poolLabel.setManaged(true);
            } else {
                poolLabel.setVisible(false);
                poolLabel.setManaged(false);
            }
            detachPoolButton.setDisable(!enableEditing || poolId == null);
            detachPoolButton.setVisible(poolId != null);
            detachPoolButton.setManaged(poolId != null);
            updateBadgeState(item);
            updating = false;
            setGraphic(container);
        }

        private void updateBadgeState(LootPoolEntryModel entry) {
            int rowIndex = getIndex();
            boolean modified = isEntryModified(rowIndex, entry);
            if (!modified) {
                badgeLabel.setVisible(false);
                badgeLabel.setManaged(false);
                return;
            }
            boolean isNew = rowIndex >= baselineEntries.size();
            badgeLabel.setText(isNew ? "New" : "Edited");
            badgeLabel.setVisible(true);
            badgeLabel.setManaged(true);
        }

        private void applyUpdate(LootPoolEntryModel updated) {
            if (updated.equals(currentEntry)) {
                return;
            }
            replaceEntryAt(getIndex(), updated);
            currentEntry = updated;
        }
    }
}
