package dev.badgersnacks.looteditor.ui;

import dev.badgersnacks.looteditor.model.EnchantmentDescriptor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Palette that mirrors the behavior of the item palette but for enchantments.
 * Designers can search across namespaces, filter by mod, and double-click to insert.
 */
public class EnchantmentPalettePane extends BorderPane {

    private final ObservableList<EnchantmentDescriptor> backingList = FXCollections.observableArrayList();
    private final FilteredList<EnchantmentDescriptor> filteredList = new FilteredList<>(backingList);
    private final ListView<EnchantmentDescriptor> listView = new ListView<>(filteredList);
    private final TextField searchField = new TextField();
    private final ComboBox<String> namespaceFilter = new ComboBox<>();
    private Consumer<EnchantmentDescriptor> insertHandler = descriptor -> { };

    public EnchantmentPalettePane() {
        buildUi();
        wireFilters();
    }

    public void setDescriptors(List<EnchantmentDescriptor> descriptors) {
        backingList.setAll(descriptors);
        namespaceFilter.getItems().setAll("All Mods");
        descriptors.stream()
                .map(EnchantmentDescriptor::namespace)
                .distinct()
                .sorted()
                .forEach(namespaceFilter.getItems()::add);
        namespaceFilter.getSelectionModel().selectFirst();
    }

    public void clear() {
        backingList.clear();
        namespaceFilter.getItems().setAll("All Mods");
        namespaceFilter.getSelectionModel().selectFirst();
    }

    public void setInsertHandler(Consumer<EnchantmentDescriptor> handler) {
        this.insertHandler = handler == null ? descriptor -> { } : handler;
    }

    private void buildUi() {
        setPadding(new Insets(6));
        Label title = new Label("Enchantment Palette");
        Label hint = new Label("Hint: double-click an enchantment to add it to the current pool.");
        hint.getStyleClass().add("hint-text");
        searchField.setPromptText("Search enchantments");
        namespaceFilter.getItems().add("All Mods");
        namespaceFilter.getSelectionModel().selectFirst();

        HBox filterRow = new HBox(6, new Label("Mod:"), namespaceFilter);
        HBox.setHgrow(namespaceFilter, Priority.ALWAYS);

        VBox header = new VBox(6, title, searchField, filterRow, hint);
        setTop(header);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EnchantmentDescriptor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.displayName() + " (" + item.id() + ")");
                }
            }
        });
        listView.setPlaceholder(new Label("No enchantments discovered yet."));
        listView.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                EnchantmentDescriptor descriptor = listView.getSelectionModel().getSelectedItem();
                if (descriptor != null) {
                    insertHandler.accept(descriptor);
                }
            }
        });
        setCenter(listView);
    }

    private void wireFilters() {
        searchField.textProperty().addListener((obs, oldV, newV) -> filteredList.setPredicate(this::matchesFilter));
        namespaceFilter.valueProperty().addListener((obs, oldV, newV) -> filteredList.setPredicate(this::matchesFilter));
        filteredList.setPredicate(this::matchesFilter);
    }

    private boolean matchesFilter(EnchantmentDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String namespaceSelection = namespaceFilter.getValue();
        if (namespaceSelection != null && !"All Mods".equals(namespaceSelection)
                && !Objects.equals(namespaceSelection, descriptor.namespace())) {
            return false;
        }
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            return true;
        }
        String lower = query.toLowerCase();
        return descriptor.displayName().toLowerCase().contains(lower)
                || descriptor.id().toLowerCase().contains(lower);
    }
}
