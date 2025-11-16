package dev.badgersnacks.looteditor.ui;

import com.fasterxml.jackson.databind.JsonNode;
import dev.badgersnacks.looteditor.agents.AgentOrchestrator;
import dev.badgersnacks.looteditor.agents.AgentResult;
import dev.badgersnacks.looteditor.agents.ItemCatalogAgentTask;
import dev.badgersnacks.looteditor.agents.ScannerAgentTask;
import dev.badgersnacks.looteditor.catalog.ItemCatalog;
import dev.badgersnacks.looteditor.catalog.ItemCatalogService;
import dev.badgersnacks.looteditor.catalog.ItemIconCache;
import dev.badgersnacks.looteditor.logging.ActionLogger;
import dev.badgersnacks.looteditor.manifest.MergedManifestLoader;
import dev.badgersnacks.looteditor.manifest.MergedManifestLoader.MergedManifest;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.model.LootTableDescriptor.SourceType;
import dev.badgersnacks.looteditor.model.LootTableTreeNode;
import dev.badgersnacks.looteditor.persistence.EnchantmentPoolLinkService;
import dev.badgersnacks.looteditor.persistence.ExportSettings;
import dev.badgersnacks.looteditor.persistence.RecentPackStorage;
import dev.badgersnacks.looteditor.persistence.RecentPackStorage.RecentPackEntry;
import dev.badgersnacks.looteditor.scanner.ModpackScanner;
import dev.badgersnacks.looteditor.model.EnchantmentDescriptor;
import dev.badgersnacks.looteditor.services.EnchantmentDataService;
import dev.badgersnacks.looteditor.services.EnchantmentPoolService;
import dev.badgersnacks.looteditor.services.LootTableService;
import dev.badgersnacks.looteditor.ui.dialogs.NewLootTableDialog;
import dev.badgersnacks.looteditor.ui.dialogs.NewLootTableRequest;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Primary layout controller responsible for wiring user actions to services and agents.
 */
public class MainView extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainView.class);
    private static final DateTimeFormatter RECENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());

    private final Stage stage;
    private final AgentOrchestrator orchestrator;
    private final ActionLogger actionLogger;
    private final ModpackScanner scanner = new ModpackScanner();
    private final LootTableService lootTableService = new LootTableService();
    private final ItemCatalogService itemCatalogService = new ItemCatalogService();
    private final ItemIconCache iconCache = new ItemIconCache();
    private final MergedManifestLoader manifestLoader = new MergedManifestLoader();
    private final Path manifestPathOverride = determineManifestOverride();
    private final ExportSettings exportSettings = new ExportSettings();

    private final TreeView<LootTableTreeNode> lootTree = new TreeView<>();
    private final TextArea inspector = new TextArea();
    private final LootTableEditorPane editorPane = new LootTableEditorPane(lootTableService, iconCache);
    private final ItemPalettePane palettePane = new ItemPalettePane(iconCache);
    private final EnchantmentPoolService enchantmentPoolService = new EnchantmentPoolService();
    private final EnchantmentPoolLinkService poolLinkService = new EnchantmentPoolLinkService();
    private final EnchantmentPoolPane enchantmentPoolPane = new EnchantmentPoolPane(enchantmentPoolService);
    private final EnchantmentDataService enchantmentDataService = new EnchantmentDataService();
    private final Label statusLabel = new Label("Select a modpack to begin.");
    private final Label manifestLabel = new Label("Manifest: n/a");
    private final Tooltip manifestTooltip = new Tooltip("Manifest status unavailable. Run refresh-loot-index first.");
    private final Button forkButton = new Button("Fork to KubeJS");
    private final Button exportDatapackButton = new Button("Export Datapack");
    private final SplitMenuButton openMenuButton = new SplitMenuButton();
    private final ComboBox<LootTableFilter> filterBox = new ComboBox<>();
    private final RecentPackStorage recentPackStorage = new RecentPackStorage();

    private Path currentModpack;
    private Path exportOverrideRoot;
    private List<LootTableDescriptor> lastDescriptors = List.of();
    private LootTableDescriptor activeDescriptor;
    private ItemCatalog currentCatalog;
    private LootTableFilter activeFilter = LootTableFilter.ALL;
    private String pendingSelectionId;
    private int lastLootCount = -1;
    private int lastItemCount = -1;
    private long lastLootDuration = -1;
    private long lastItemDuration = -1;

    public MainView(Stage stage, AgentOrchestrator orchestrator, ActionLogger actionLogger) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.actionLogger = Objects.requireNonNull(actionLogger, "actionLogger");
        buildLayout();
        wireListeners();
        palettePane.setInsertHandler(editorPane::addEntry);
        enchantmentPoolPane.setAttachHandler(poolId -> {
            editorPane.attachPoolToSelection(poolId);
            statusLabel.setText("Attached pool " + poolId + " to selection.");
        });
        enchantmentPoolPane.setPoolsChangedCallback(editorPane::refreshEnchantmentPools);
        enchantmentPoolPane.setEnchantmentCatalog(List.of());
        manifestLabel.setTooltip(manifestTooltip);
        manifestTooltip.setWrapText(true);
        manifestTooltip.setPrefWidth(360);
        setManifestStatus("Manifest: n/a", "Run ./gradlew refreshLootIndex before scanning to compare coverage.");
        refreshRecentMenuItems();
        configureCloseWarning();
    }

    private void buildLayout() {
        setPadding(new Insets(10));

        ToolBar toolbar = new ToolBar();
        openMenuButton.setText("Open Modpack");
        openMenuButton.setOnAction(e -> chooseModpack());
        Button rescanButton = new Button("Rescan");
        rescanButton.setOnAction(e -> {
            if (currentModpack != null) {
                scanModpack(currentModpack);
            }
        });
        Button newTableButton = new Button("New Loot Table");
        newTableButton.setOnAction(e -> openNewTableDialog());
        toolbar.getItems().addAll(openMenuButton, rescanButton, newTableButton, new Separator(), statusLabel,
                new Separator(), manifestLabel);
        setTop(toolbar);

        lootTree.setShowRoot(false);
        lootTree.setPrefWidth(280);
        lootTree.setRoot(new TreeItem<>(new LootTableTreeNode("loot-root")));
        Label treeLabel = new Label("Loot Tables");
        filterBox.getItems().addAll(LootTableFilter.values());
        filterBox.getSelectionModel().select(LootTableFilter.ALL);
        VBox leftPane = new VBox(6, treeLabel, filterBox, lootTree);
        VBox.setVgrow(lootTree, Priority.ALWAYS);
        setLeft(leftPane);

        SplitPane centerSplit = new SplitPane();
        centerSplit.getItems().add(editorPane);

        inspector.setEditable(false);
        inspector.setWrapText(true);
        inspector.setPrefWidth(380);
        inspector.setPromptText("Table metadata and validation messages will land here.");
        forkButton.setDisable(true);
        forkButton.setOnAction(e -> forkActiveDescriptor());
        exportDatapackButton.setDisable(true);
        exportDatapackButton.setOnAction(e -> exportActiveDescriptorToDatapack());
        VBox inspectorBox = new VBox(6, new Label("Inspector"), inspector, forkButton, exportDatapackButton);
        VBox.setVgrow(inspector, Priority.ALWAYS);

        TabPane sideTabs = new TabPane();
        Tab inspectorTab = new Tab("Inspector", inspectorBox);
        inspectorTab.setClosable(false);
        Tab paletteTab = new Tab("Item Palette", palettePane);
        paletteTab.setClosable(false);
        Tab poolsTab = new Tab("Enchant Pools", enchantmentPoolPane);
        poolsTab.setClosable(false);
        sideTabs.getTabs().addAll(inspectorTab, paletteTab, poolsTab);

        centerSplit.getItems().add(sideTabs);
        centerSplit.setDividerPositions(0.54);
        setCenter(centerSplit);

        editorPane.setOnSave(this::saveActiveDescriptor);
    }

    private void configureCloseWarning() {
        stage.setOnCloseRequest(this::handleCloseRequest);
    }

    private void handleCloseRequest(WindowEvent event) {
        boolean lootDirty = editorPane.hasUnsavedChanges();
        boolean poolDirty = enchantmentPoolPane.hasUnsavedChanges();
        if (!lootDirty && !poolDirty) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Unsaved work");
        alert.setHeaderText("You have unsaved changes.");
        StringBuilder details = new StringBuilder();
        if (lootDirty) {
            details.append("- Loot table edits are pending.\n");
        }
        if (poolDirty) {
            details.append("- Enchantment pool edits are pending.\n");
        }
        details.append("\nExit without saving?");
        alert.setContentText(details.toString());
        ButtonType exitAnyway = new ButtonType("Discard and Exit", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(exitAnyway, cancel);
        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() != exitAnyway) {
            event.consume();
        } else {
            actionLogger.log("ui:closeWithoutSaving",
                    "User dismissed unsaved work (lootDirty=" + lootDirty + ", poolDirty=" + poolDirty + ").");
        }
    }

    private void wireListeners() {
        lootTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getValue() == null || !newV.getValue().isLeaf()) {
                activeDescriptor = null;
                updateActionButtons();
                return;
            }
            LootTableDescriptor descriptor = newV.getValue().descriptor();
            loadDescriptor(descriptor);
        });

        filterBox.valueProperty().addListener((obs, oldV, newV) -> {
            activeFilter = newV == null ? LootTableFilter.ALL : newV;
            rebuildTree();
        });
    }

    private void chooseModpack() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Minecraft Modpack Root");
        if (currentModpack != null) {
            chooser.setInitialDirectory(currentModpack.toFile());
        } else {
            Path cwd = Path.of("").toAbsolutePath();
            if (Files.isDirectory(cwd)) {
                chooser.setInitialDirectory(cwd.toFile());
            }
        }
        var selected = chooser.showDialog(stage);
        if (selected != null) {
            Path chosen = selected.toPath();
            actionLogger.log("ui:openModpackBrowse", "Selected modpack: " + chosen);
            scanModpack(chosen);
        } else {
            actionLogger.log("ui:openModpackBrowse", "User cancelled modpack selection.");
        }
    }

    private void openRecentModpack(RecentPackEntry entry) {
        Path path = entry.toPath();
        if (!Files.isDirectory(path)) {
            actionLogger.log("ui:openModpackCached", "Recent modpack missing: " + path);
            recentPackStorage.remove(path);
            refreshRecentMenuItems();
            showError("Recent modpack missing", new IOException("Directory does not exist: " + path));
            return;
        }
        actionLogger.log("ui:openModpackCached", "Selected recent modpack: " + path);
        scanModpack(path);
    }

    private void refreshRecentMenuItems() {
        openMenuButton.getItems().clear();
        List<RecentPackEntry> entries = recentPackStorage.getEntries();
        if (entries.isEmpty()) {
            MenuItem emptyItem = new MenuItem("No recent modpacks");
            emptyItem.setDisable(true);
            openMenuButton.getItems().add(emptyItem);
            return;
        }
        for (RecentPackEntry entry : entries) {
            MenuItem item = new MenuItem(formatRecentEntry(entry));
            item.setOnAction(e -> openRecentModpack(entry));
            openMenuButton.getItems().add(item);
        }
    }

    private String formatRecentEntry(RecentPackEntry entry) {
        Path path = Path.of(entry.path());
        String folderName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        String timestamp = entry.lastOpened() != null
                ? RECENT_TIME_FORMAT.format(entry.lastOpened())
                : "unknown";
        return folderName + " (" + timestamp + ")";
    }

    public void scanModpack(Path modpackRoot) {
        this.currentModpack = modpackRoot;
        this.exportOverrideRoot = modpackRoot == null
                ? null
                : exportSettings.resolvePackRoot(modpackRoot).orElse(null);
        try {
            List<Path> syncedWorlds = lootTableService.syncWorldDatapacks(modpackRoot);
            if (!syncedWorlds.isEmpty()) {
                String worldNames = syncedWorlds.stream()
                        .map(path -> path.getFileName() != null ? path.getFileName().toString() : path.toString())
                        .collect(Collectors.joining(", "));
                actionLogger.log("datapack:sync", "Synced datapack into worlds: " + worldNames);
            }
        } catch (IOException e) {
            actionLogger.log("datapack:syncError", "Failed to sync datapack into worlds.", e);
            showNotification("Warning: unable to sync datapack into saves (" + e.getMessage() + ")", Alert.AlertType.WARNING);
        }
        editorPane.configurePoolContext(modpackRoot, enchantmentPoolService, poolLinkService);
        // keep the pool editor/palette in sync with whatever pack we just scanned
        enchantmentPoolPane.setModpackRoot(modpackRoot);
        loadEnchantmentCatalog(modpackRoot);
        recentPackStorage.record(modpackRoot);
        refreshRecentMenuItems();
        actionLogger.log("scan:request", "Scanning modpack: " + modpackRoot.toAbsolutePath());
        statusLabel.setText("Scanning " + modpackRoot + " ...");
        lootTree.getRoot().getChildren().clear();
        editorPane.clear();
        inspector.clear();
        palettePane.clearCatalog();
        forkButton.setDisable(true);
        lastLootCount = -1;
        lastItemCount = -1;
        lastLootDuration = -1;
        lastItemDuration = -1;
        setManifestStatus("Manifest: analyzing...", "Checking merged manifest for " + modpackRoot + " ...");

        CompletableFuture<AgentResult<List<LootTableDescriptor>>> tablesFuture =
                orchestrator.submit(new ScannerAgentTask(modpackRoot, scanner));
        tablesFuture.whenComplete((result, error) -> {
            if (error != null) {
                actionLogger.log("scan:error", "Loot table scan failed for " + modpackRoot, error);
                Platform.runLater(() -> showError("Loot table scan failed", error));
                return;
            }
            Platform.runLater(() -> applyScan(result.payload(), result.duration().toMillis()));
        });

        CompletableFuture<AgentResult<ItemCatalog>> catalogFuture =
                orchestrator.submit(new ItemCatalogAgentTask(modpackRoot, itemCatalogService));
        catalogFuture.whenComplete((result, error) -> {
            if (error != null) {
                actionLogger.log("scan:error", "Item catalog scan failed for " + modpackRoot, error);
                Platform.runLater(() -> showError("Item catalog scan failed", error));
                return;
            }
            Platform.runLater(() -> applyCatalog(result.payload(), result.duration().toMillis()));
        });
    }

    private void applyScan(List<LootTableDescriptor> descriptors, long durationMs) {
        List<LootTableDescriptor> deduped = dedupeDescriptors(descriptors);
        this.lastDescriptors = deduped;
        this.lastLootCount = deduped.size();
        this.lastLootDuration = durationMs;
        actionLogger.log("scan:lootComplete",
                "Loaded " + deduped.size() + " loot tables in " + durationMs + " ms.");
        rebuildTree();
        analyzeManifest(currentModpack, descriptors);
        updateStatus();
        selectPendingDescriptor();
    }

    private void rebuildTree() {
        Map<SourceType, Map<String, List<LootTableDescriptor>>> grouped = lastDescriptors.stream()
                .filter(activeFilter::matches)
                .collect(Collectors.groupingBy(LootTableDescriptor::sourceType,
                        Collectors.groupingBy(LootTableDescriptor::namespace)));
        TreeItem<LootTableTreeNode> root = lootTree.getRoot();
        root.getChildren().clear();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(SourceType::name)))
                .forEach(entry -> {
                    TreeItem<LootTableTreeNode> sourceNode = new TreeItem<>(new LootTableTreeNode(entry.getKey().label()));
                    entry.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(namespaceEntry -> {
                                TreeItem<LootTableTreeNode> namespaceNode =
                                        new TreeItem<>(new LootTableTreeNode(namespaceEntry.getKey()));
                                namespaceEntry.getValue().stream()
                                        .sorted(Comparator.comparing(LootTableDescriptor::tablePath))
                                        .forEach(descriptor -> namespaceNode.getChildren().add(
                                                new TreeItem<>(new LootTableTreeNode(descriptor.tablePath(), descriptor))));
                                sourceNode.getChildren().add(namespaceNode);
                            });
                    root.getChildren().add(sourceNode);
                });
        lootTree.getRoot().setExpanded(true);
    }

    private void selectPendingDescriptor() {
        if (pendingSelectionId == null) {
            return;
        }
        TreeItem<LootTableTreeNode> match = findDescriptorItem(lootTree.getRoot(), pendingSelectionId);
        if (match != null) {
            lootTree.getSelectionModel().select(match);
            int row = lootTree.getRow(match);
            if (row >= 0) {
                lootTree.scrollTo(row);
            }
        }
        pendingSelectionId = null;
    }

    private TreeItem<LootTableTreeNode> findDescriptorItem(TreeItem<LootTableTreeNode> root, String qualifiedId) {
        if (root == null) {
            return null;
        }
        LootTableTreeNode node = root.getValue();
        if (node != null && node.isLeaf() && node.descriptor() != null &&
                node.descriptor().qualifiedName().equals(qualifiedId)) {
            return root;
        }
        for (TreeItem<LootTableTreeNode> child : root.getChildren()) {
            TreeItem<LootTableTreeNode> match = findDescriptorItem(child, qualifiedId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private void applyCatalog(ItemCatalog catalog, long durationMs) {
        this.currentCatalog = catalog;
        this.lastItemCount = catalog.descriptors().size();
        this.lastItemDuration = durationMs;
        actionLogger.log("scan:catalogComplete",
                "Loaded " + lastItemCount + " palette entries in " + durationMs + " ms.");
        iconCache.clear();
        palettePane.displayCatalog(catalog);
        editorPane.setItemCatalog(catalog);
        updateStatus();
    }

    private void loadDescriptor(LootTableDescriptor descriptor) {
        try {
            LOGGER.info("Loading descriptor {} from {}", descriptor.qualifiedName(), descriptor.containerPath());
            JsonNode node = lootTableService.load(descriptor);
            this.activeDescriptor = descriptor;
            editorPane.displayDescriptor(descriptor, node);
            inspector.setText(buildInspectorBlock(descriptor));
            updateActionButtons();
            actionLogger.log("editor:load", "Loaded " + descriptor.qualifiedName());
        } catch (IOException e) {
            actionLogger.log("editor:loadError", "Unable to load " + descriptor.qualifiedName(), e);
            showError("Unable to load " + descriptor.qualifiedName(), e);
        }
    }

    private void updateActionButtons() {
        boolean hasDescriptor = activeDescriptor != null && currentModpack != null;
        boolean canFork = hasDescriptor && !activeDescriptor.editable();
        forkButton.setDisable(!canFork);
        exportDatapackButton.setDisable(!hasDescriptor);
    }

    private String buildInspectorBlock(LootTableDescriptor descriptor) {
        return "Name: " + descriptor.qualifiedName() + System.lineSeparator() +
                "Source: " + descriptor.sourceDisplay() + System.lineSeparator() +
                "Editable: " + descriptor.editable() + System.lineSeparator() +
                "Location: " + descriptor.containerPath() + (descriptor.isArchiveEntry()
                ? " :: " + descriptor.archiveEntry()
                : "");
    }

    private void saveActiveDescriptor(JsonNode updatedNode) {
        if (activeDescriptor == null || currentModpack == null) {
            return;
        }
        try {
            LOGGER.info("Saving loot table {} ({} entries) to datapack.", activeDescriptor.qualifiedName(),
                    editorPane.entryCount());
            LootTableDescriptor saved = lootTableService.saveToPreferredLocation(currentModpack, activeDescriptor, updatedNode);
            this.activeDescriptor = saved;
            inspector.setText(buildInspectorBlock(saved));
            editorPane.displayDescriptor(saved, updatedNode.deepCopy());
            statusLabel.setText("Saved " + saved.qualifiedName() + " to datapack");
            actionLogger.log("editor:save",
                    "Saved " + saved.qualifiedName() + " to " + saved.containerPath() + " entries=" + editorPane.entryCount());
            updateActionButtons();
            updateDescriptorEntry(saved);
        } catch (IOException e) {
            actionLogger.log("editor:saveError", "Failed to save " + activeDescriptor.qualifiedName(), e);
            showError("Failed to save " + activeDescriptor.qualifiedName(), e);
        }
    }

    private void openNewTableDialog() {
        if (currentModpack == null) {
            showError("Open a modpack first", new IllegalStateException("No modpack selected"));
            return;
        }
        NewLootTableDialog dialog = new NewLootTableDialog(stage);
        dialog.setTitle("Create Loot Table");
        dialog.showAndWait().ifPresent(request -> {
            try {
                LootTableDescriptor descriptor = lootTableService.createTable(
                        currentModpack,
                        request.namespace(),
                        request.tablePath(),
                        request.template());
                pendingSelectionId = descriptor.qualifiedName();
                actionLogger.log("editor:create", "Created new table " + descriptor.qualifiedName());
                scanModpack(currentModpack);
            } catch (IOException e) {
                actionLogger.log("editor:createError", "Unable to create loot table", e);
                showError("Unable to create loot table", e);
            }
        });
    }

    private void forkActiveDescriptor() {
        if (currentModpack == null || activeDescriptor == null || activeDescriptor.editable()) {
            return;
        }
        try {
            LootTableDescriptor descriptor = lootTableService.forkToKubeJs(currentModpack, activeDescriptor);
            pendingSelectionId = descriptor.qualifiedName();
            statusLabel.setText("Forked " + descriptor.qualifiedName() + " into KubeJS");
            actionLogger.log("editor:fork", "Forked " + descriptor.qualifiedName() + " to kubejs/data");
            scanModpack(currentModpack);
        } catch (IOException e) {
            actionLogger.log("editor:forkError", "Unable to fork " + activeDescriptor.qualifiedName(), e);
            showError("Unable to fork loot table", e);
        }
    }

    private void exportActiveDescriptorToDatapack() {
        if (currentModpack == null || activeDescriptor == null) {
            showNotification("No modpack open", Alert.AlertType.WARNING);
            return;
        }
        Path targetRoot = currentModpack;
        try {
            LOGGER.info("Exporting loot table {} ({} entries) to datapack.", activeDescriptor.qualifiedName(),
                    editorPane.entryCount());
            LootTableDescriptor descriptor = lootTableService.exportToDatapack(targetRoot, activeDescriptor);
            JsonNode node = lootTableService.load(descriptor);
            this.activeDescriptor = descriptor;
            editorPane.displayDescriptor(descriptor, node);
            inspector.setText(buildInspectorBlock(descriptor));
            actionLogger.log("editor:exportDatapack",
                    "Exported " + descriptor.qualifiedName() + " to " + targetRoot + " entries=" + editorPane.entryCount());
            statusLabel.setText("Exported " + descriptor.qualifiedName() + " to datapack");
            showNotification("Datapack export complete:\n" + descriptor.qualifiedName(), Alert.AlertType.INFORMATION);
            updateDescriptorEntry(descriptor);
        } catch (IOException e) {
            actionLogger.log("editor:exportDatapackError",
                    "Unable to export " + activeDescriptor.qualifiedName(), e);
            showNotification("Export failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void updateStatus() {
        String lootPart = lastLootCount >= 0
                ? "Loot tables: " + lastLootCount + formatDuration(lastLootDuration)
                : "Loot tables: ...";
        String itemPart = lastItemCount >= 0
                ? "Items: " + lastItemCount + formatDuration(lastItemDuration)
                : "Items: ...";
        statusLabel.setText(lootPart + " | " + itemPart);
    }

    private String formatDuration(long durationMs) {
        return durationMs >= 0 ? " (" + durationMs + " ms)" : "";
    }

    private void analyzeManifest(Path modpackRoot, List<LootTableDescriptor> descriptors) {
        if (modpackRoot == null) {
            setManifestStatus("Manifest: n/a", "Select a modpack to analyze manifest coverage.");
            actionLogger.log("manifest:status", "No modpack selected for manifest analysis.");
            return;
        }
        Path manifestPath = resolveManifestPath();
        if (!Files.exists(manifestPath)) {
            setManifestStatus("Manifest: missing", "Manifest not found at:\n" + manifestPath
                    + "\nRun ./gradlew refreshLootIndex -PpackRoot=\"" + modpackRoot + "\".");
            actionLogger.log("manifest:missing", "Manifest not found at " + manifestPath);
            return;
        }
        try {
            MergedManifest manifest = manifestLoader.load(manifestPath).orElse(null);
            if (manifest == null) {
                setManifestStatus("Manifest: unreadable", "Unable to parse manifest at " + manifestPath);
                actionLogger.log("manifest:unreadable", "Unable to parse manifest at " + manifestPath);
                return;
            }
            Path targetRoot = manifest.packRoot();
            Path normalizedPack = modpackRoot.toAbsolutePath().normalize();
            if (targetRoot != null && !normalizedPack.equals(targetRoot)) {
                setManifestStatus("Manifest: other pack",
                        "Manifest targets " + targetRoot + "\nCurrent pack: " + normalizedPack);
                actionLogger.log("manifest:mismatch",
                        "Manifest targets " + targetRoot + " but pack is " + normalizedPack);
                return;
            }
            Set<String> scannedIds = descriptors.stream()
                    .map(LootTableDescriptor::qualifiedName)
                    .collect(Collectors.toSet());
            List<String> missing = manifest.tableIds().stream()
                    .filter(id -> !scannedIds.contains(id))
                    .sorted()
                    .collect(Collectors.toList());
            if (missing.isEmpty()) {
                setManifestStatus("Manifest: synced (" + manifest.tableIds().size() + ")",
                        "All manifest entries accounted for.\nSource: " + manifest.manifestPath());
                actionLogger.log("manifest:synced",
                        "All " + manifest.tableIds().size() + " manifest entries found.");
            } else {
                LOGGER.warn("Manifest entries missing from scan: {}", missing);
                setManifestStatus("Manifest missing: " + missing.size(),
                        buildMissingTooltip(missing, manifest.manifestPath()));
                actionLogger.log("manifest:missingEntries",
                        missing.size() + " manifest entries missing from live scan.");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read manifest {}", manifestPath, e);
            setManifestStatus("Manifest: error", "Failed to read manifest:\n" + e.getMessage());
            actionLogger.log("manifest:error", "Failed to read manifest " + manifestPath, e);
        }
    }

    private void setManifestStatus(String text, String tooltip) {
        manifestLabel.setText(text);
        manifestTooltip.setText(tooltip);
    }

    private Path resolveManifestPath() {
        if (manifestPathOverride != null) {
            return manifestPathOverride;
        }
        Path cwdManifest = Path.of("import", "loot_tables_merged.json").toAbsolutePath().normalize();
        if (Files.exists(cwdManifest)) {
            return cwdManifest;
        }
        String appHome = System.getProperty("app.home");
        if (appHome != null && !appHome.isBlank()) {
            Path appManifest = Path.of(appHome, "import", "loot_tables_merged.json").toAbsolutePath().normalize();
            if (Files.exists(appManifest)) {
                return appManifest;
            }
        }
        return cwdManifest;
    }

    private Path determineManifestOverride() {
        String override = System.getProperty("loot.manifest");
        if (override == null || override.isBlank()) {
            return null;
        }
        try {
            return Path.of(override).toAbsolutePath().normalize();
        } catch (Exception e) {
            LOGGER.warn("Invalid loot.manifest value '{}'", override, e);
            return null;
        }
    }

    private String buildMissingTooltip(List<String> missing, Path manifestPath) {
        StringBuilder builder = new StringBuilder();
        builder.append("Missing manifest entries (").append(missing.size()).append(")")
                .append(System.lineSeparator());
        missing.stream().limit(20).forEach(id -> builder.append(" • ").append(id).append(System.lineSeparator()));
        if (missing.size() > 20) {
            builder.append("... and ").append(missing.size() - 20).append(" more")
                    .append(System.lineSeparator());
        }
        builder.append("Manifest: ").append(manifestPath);
        return builder.toString();
    }

    private void showError(String message, Throwable throwable) {
        statusLabel.setText(message);
        actionLogger.log("ui:error", message, throwable);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Loot Editor B");
        alert.setHeaderText(message);
        alert.setContentText(throwable.getMessage());
        alert.show();
    }

    private void showNotification(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Loot Editor B");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private void updateDescriptorEntry(LootTableDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        List<LootTableDescriptor> updated = new ArrayList<>(lastDescriptors);
        boolean replaced = false;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).qualifiedName().equals(descriptor.qualifiedName())) {
                updated.set(i, descriptor);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updated.add(descriptor);
            LOGGER.info("Added new descriptor {} to cache after export/save.", descriptor.qualifiedName());
        } else {
            LOGGER.info("Updated descriptor {} in cache after export/save.", descriptor.qualifiedName());
        }
        this.lastDescriptors = dedupeDescriptors(updated);
        pendingSelectionId = descriptor.qualifiedName();
        rebuildTree();
        selectPendingDescriptor();
    }

    private List<LootTableDescriptor> dedupeDescriptors(List<LootTableDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty() || currentModpack == null) {
            return descriptors == null ? List.of() : descriptors;
        }
        Map<String, LootTableDescriptor> best = new LinkedHashMap<>();
        for (LootTableDescriptor descriptor : descriptors) {
            String key = descriptor.sourceType() + "::" + descriptor.qualifiedName();
            LootTableDescriptor existing = best.get(key);
            if (existing == null || descriptorPriority(descriptor) > descriptorPriority(existing)) {
                best.put(key, descriptor);
            }
        }
        return new ArrayList<>(best.values());
    }

    private int descriptorPriority(LootTableDescriptor descriptor) {
        if (descriptor == null) {
            return 0;
        }
        if (descriptor.sourceType() != SourceType.DATAPACK || currentModpack == null) {
            return 2;
        }
        Path path = descriptor.containerPath().toAbsolutePath().normalize();
        if (exportOverrideRoot != null && path.startsWith(exportOverrideRoot)) {
            return 4;
        }
        Path packDatapacks = currentModpack.resolve("datapacks").toAbsolutePath().normalize();
        Path savesDir = currentModpack.resolve("saves").toAbsolutePath().normalize();
        if (path.startsWith(packDatapacks)) {
            return 3;
        }
        if (path.startsWith(savesDir)) {
            return 1;
        }
        return 2;
    }

    /**
     * Loads (or clears) the enchantment palette so the pool editor has the latest registry entries.
     */
    private void loadEnchantmentCatalog(Path modpackRoot) {
        if (modpackRoot == null) {
            enchantmentPoolPane.setEnchantmentCatalog(List.of());
            return;
        }
        List<EnchantmentDescriptor> descriptors = enchantmentDataService.load(modpackRoot);
        enchantmentPoolPane.setEnchantmentCatalog(descriptors);
    }

    private enum LootTableFilter {
        ALL("All Tables", descriptor -> true),
        CHESTS("Chests", descriptor -> descriptor.tablePath().contains("chest")),
        BLOCKS("Blocks", descriptor -> descriptor.tablePath().startsWith("blocks/")
                || descriptor.tablePath().contains("/blocks/")),
        ENTITIES("Entities", descriptor -> descriptor.tablePath().startsWith("entities/")
                || descriptor.tablePath().contains("/entities/"));

        private final String label;
        private final Predicate<LootTableDescriptor> predicate;

        LootTableFilter(String label, Predicate<LootTableDescriptor> predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        boolean matches(LootTableDescriptor descriptor) {
            return predicate.test(descriptor);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
