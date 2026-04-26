package dev.darshan.agentrouter.routing;

import java.util.Optional;

/**
 * Strategy interface for intent classification.
 * Pluggable design: swap keyword-based for LLM-based without changing the pipeline.
 *
 * Current implementations:
 * - {@link KeywordClassifier} — regex/keyword matching (fast, deterministic)
 *
 * Future: LLMClassifier can be added without modifying StateGraph.
 */
public interface IntentClassifier {

    /**
     * Classify a user request into a known intent.
     *
     * @param userRequest raw user input (e.g., "What's the weather in SF?")
     * @return the classified intent (e.g., "weather_query"), or empty if unrecognized
     */
    Optional<String> classify(String userRequest);
}
