package dev.badgersnacks.looteditor.ui;

import dev.badgersnacks.looteditor.catalog.ItemCatalog;
import dev.badgersnacks.looteditor.catalog.ItemDescriptor;
import dev.badgersnacks.looteditor.catalog.ItemIconCache;
import dev.badgersnacks.looteditor.model.LootPoolEntryModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * JEI-style palette of every item/block the scanner discovers.
 */
public class ItemPalettePane extends BorderPane {

    private final ItemIconCache iconCache;
    private final ListView<ItemDescriptor> listView = new ListView<>();
    private final ObservableList<ItemDescriptor> backingList = FXCollections.observableArrayList();
    private final FilteredList<ItemDescriptor> filteredList = new FilteredList<>(backingList);
    private final TextField searchField = new TextField();
    private final ComboBox<String> namespaceFilter = new ComboBox<>();
    private final ComboBox<TypeFilter> typeFilter = new ComboBox<>();
    private Consumer<LootPoolEntryModel> insertHandler = entry -> { };
    private ItemCatalog catalog;

    public ItemPalettePane(ItemIconCache iconCache) {
        this.iconCache = Objects.requireNonNull(iconCache, "iconCache");
        buildUi();
        wireFilters();
    }

    private void buildUi() {
        setPadding(new Insets(5));
        VBox header = new VBox(6);
        Label title = new Label("Item Palette");
        searchField.setPromptText("Search (name or namespace:item)");

        namespaceFilter.getItems().add("All Mods");
        namespaceFilter.getSelectionModel().selectFirst();

        typeFilter.getItems().addAll(TypeFilter.values());
        typeFilter.getSelectionModel().select(TypeFilter.ALL);

        HBox filterRow = new HBox(6, new Label("Mod:"), namespaceFilter, new Label("Type:"), typeFilter);
        filterRow.setFillHeight(true);
        HBox.setHgrow(namespaceFilter, Priority.ALWAYS);

        header.getChildren().addAll(title, searchField, filterRow);

        listView.setItems(filteredList);
        listView.setCellFactory(lv -> new DescriptorCell());
        listView.setPlaceholder(new Label("Scan a modpack to load items."));
        VBox.setVgrow(listView, Priority.ALWAYS);

        setTop(header);
        setCenter(listView);

        listView.setOnDragDetected(event -> {
            ItemDescriptor descriptor = listView.getSelectionModel().getSelectedItem();
            if (descriptor == null) {
                return;
            }
            Dragboard dragboard = listView.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(descriptor.qualifiedId() + "|1|minecraft:item|1|1");
            dragboard.setContent(content);
            event.consume();
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ItemDescriptor descriptor = listView.getSelectionModel().getSelectedItem();
                if (descriptor != null) {
                    insertHandler.accept(new LootPoolEntryModel(descriptor.qualifiedId(), 1.0d, "minecraft:item", 1, 1, null));
                }
            }
        });
    }

    private void wireFilters() {
        searchField.textProperty().addListener((obs, oldV, newV) -> filteredList.setPredicate(item -> filterItem(item)));
        namespaceFilter.valueProperty().addListener((obs, oldV, newV) -> filteredList.setPredicate(this::filterItem));
        typeFilter.valueProperty().addListener((obs, oldV, newV) -> filteredList.setPredicate(this::filterItem));
        filteredList.setPredicate(this::filterItem);
    }

    private boolean filterItem(ItemDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String namespaceSelection = namespaceFilter.getValue();
        if (namespaceSelection != null && !"All Mods".equals(namespaceSelection) && !descriptor.namespace().equals(namespaceSelection)) {
            return false;
        }
        TypeFilter filter = typeFilter.getValue() == null ? TypeFilter.ALL : typeFilter.getValue();
        if (!filter.matches(descriptor)) {
            return false;
        }
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            return true;
        }
        String lower = query.toLowerCase();
        return descriptor.displayName().toLowerCase().contains(lower) ||
                descriptor.qualifiedId().toLowerCase().contains(lower);
    }

    public void displayCatalog(ItemCatalog catalog) {
        this.catalog = catalog;
        backingList.setAll(catalog.descriptors());
        namespaceFilter.getItems().setAll("All Mods");
        namespaceFilter.getItems().addAll(catalog.namespaces());
        namespaceFilter.getSelectionModel().selectFirst();
    }

    public void clearCatalog() {
        this.catalog = null;
        backingList.clear();
        namespaceFilter.getItems().setAll("All Mods");
        namespaceFilter.getSelectionModel().selectFirst();
    }

    public void setInsertHandler(Consumer<LootPoolEntryModel> insertHandler) {
        this.insertHandler = insertHandler == null ? entry -> { } : insertHandler;
    }

    public ItemCatalog currentCatalog() {
        return catalog;
    }

    private class DescriptorCell extends ListCell<ItemDescriptor> {
        private final ImageView iconView = new ImageView();
        private final Label nameLabel = new Label();
        private final Label subLabel = new Label();
        private final VBox textBox = new VBox(nameLabel, subLabel);
        private final HBox container = new HBox(8, iconView, textBox);

        DescriptorCell() {
            iconView.setFitWidth(24);
            iconView.setFitHeight(24);
            subLabel.getStyleClass().add("palette-sub");
        }

        @Override
        protected void updateItem(ItemDescriptor item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            iconView.setImage(iconCache.imageFor(item));
            nameLabel.setText(item.displayName());
            subLabel.setText(item.qualifiedId());
            setGraphic(container);
        }
    }

    private enum TypeFilter {
        ALL("All", descriptor -> true),
        ITEM("Items", descriptor -> descriptor.type() == ItemDescriptor.ItemType.ITEM),
        BLOCK("Blocks", descriptor -> descriptor.type() == ItemDescriptor.ItemType.BLOCK);

        private final String label;
        private final java.util.function.Predicate<ItemDescriptor> predicate;

        TypeFilter(String label, java.util.function.Predicate<ItemDescriptor> predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        @Override
        public String toString() {
            return label;
        }

        boolean matches(ItemDescriptor descriptor) {
            return predicate.test(descriptor);
        }
    }
}
