package dev.badgersnacks.looteditor;

import dev.badgersnacks.looteditor.agents.AgentOrchestrator;
import dev.badgersnacks.looteditor.logging.ActionLogger;
import dev.badgersnacks.looteditor.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class LootEditorApp extends Application {

    private AgentOrchestrator orchestrator;
    private ActionLogger actionLogger;

    @Override
    public void init() {
        orchestrator = new AgentOrchestrator();
        actionLogger = new ActionLogger();
        actionLogger.log("app:init", "Application initialized.");
    }

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView(stage, orchestrator, actionLogger);
        Scene scene = new Scene(mainView, 1280, 800);
        stage.setTitle("Loot Editor B");
        stage.setScene(scene);
        stage.show();

        String preloadedPath = System.getProperty("launcher");
        if (preloadedPath != null && !preloadedPath.isBlank()) {
            mainView.scanModpack(Path.of(preloadedPath));
        }
    }

    @Override
    public void stop() {
        if (actionLogger != null) {
            actionLogger.log("app:stop", "Application shutting down.");
        }
        orchestrator.close();
        if (actionLogger != null) {
            actionLogger.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
