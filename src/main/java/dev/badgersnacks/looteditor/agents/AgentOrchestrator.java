package dev.badgersnacks.looteditor.agents;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Lightweight executor facade that keeps agent jobs off the JavaFX thread.
 */
public class AgentOrchestrator implements AutoCloseable {

    private final ExecutorService executorService;

    public AgentOrchestrator() {
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new AgentThreadFactory());
    }

    public <T> CompletableFuture<AgentResult<T>> submit(AgentTask<T> task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.supplyAsync(() -> execute(task), executorService);
    }

    private <T> AgentResult<T> execute(AgentTask<T> task) {
        Instant start = Instant.now();
        try {
            T payload = task.run();
            return new AgentResult<>(task.name(), payload, Duration.between(start, Instant.now()));
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    private static class AgentThreadFactory implements ThreadFactory {
        private int counter = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "agent-worker-" + counter++);
            t.setDaemon(true);
            return t;
        }
    }
}
