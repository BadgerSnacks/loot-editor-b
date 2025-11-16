package dev.badgersnacks.looteditor.agents;

import java.time.Duration;

/**
 * Simple value object describing the result of an agent run.
 */
public record AgentResult<T>(String agentName, T payload, Duration duration) {
}
