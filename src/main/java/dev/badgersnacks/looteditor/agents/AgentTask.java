package dev.badgersnacks.looteditor.agents;

/**
 * Represents a unit of work that can be executed by the AgentOrchestrator.
 * Keeping these tasks small allows the app to delegate expensive or noisy
 * operations (like scanning thousands of files) without bloating UI context.
 */
public interface AgentTask<T> {
    String name();
    T run() throws Exception;
}
