package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline Node 1: ResolveIntent
 *
 * Responsibilities:
 * 1. Classify user intent using IntentClassifier (Strategy pattern)
 * 2. Look up matching tool from ToolRegistry
 * 3. Extract parameters from user request
 * 4. Populate context.intent, context.selectedTool, context.toolInput
 */
@Component
public class ResolveIntentNode {

    private static final Logger log = LoggerFactory.getLogger(ResolveIntentNode.class);

    private final IntentClassifier classifier;
    private final ToolRegistry registry;

    public ResolveIntentNode(IntentClassifier classifier, ToolRegistry registry) {
        this.classifier = classifier;
        this.registry = registry;
    }

    /**
     * Execute intent resolution: classify → find tool → extract params.
     */
    public ExecutionContext execute(ExecutionContext context) {
        log.info("ResolveIntent: processing '{}'", context.getUserRequest());

        // Step 1: Classify intent
        Optional<String> intentOpt = classifier.classify(context.getUserRequest());
        if (intentOpt.isEmpty()) {
            log.warn("ResolveIntent: could not classify '{}'", context.getUserRequest());
            return context.withError(new RuntimeException(
                    "Could not classify intent. Try: 'weather in <city>', 'get customer <id>', 'calculate 5 + 3'"));
        }

        String intent = intentOpt.get();
        context.withIntent(intent);
        log.info("ResolveIntent: classified as '{}'", intent);

        // Step 2: Find matching tool
        Optional<Tool> toolOpt = registry.findByIntent(intent);
        if (toolOpt.isEmpty()) {
            log.warn("ResolveIntent: no tool found for intent '{}'", intent);
            return context.withError(new RuntimeException(
                    "No tool registered for intent: " + intent));
        }

        Tool tool = toolOpt.get();
        context.withSelectedTool(tool);
        log.info("ResolveIntent: selected tool '{}'", tool.getName());

        // Step 3: Extract parameters
        Map<String, Object> params = extractParameters(context.getUserRequest(), intent);
        context.withToolInput(params);
        log.info("ResolveIntent: extracted params {}", params);

        context.getMetrics().setToolName(tool.getName());
        return context;
    }

    /**
     * Extract parameters from user request based on intent.
     * Uses regex patterns to pull structured data from natural language.
     */
    Map<String, Object> extractParameters(String userRequest, String intent) {
        Map<String, Object> params = new HashMap<>();

        switch (intent) {
            case "weather_query" -> {
                // Extract city name/code from patterns like:
                // "weather in SF", "What's the temperature in NYC", "forecast LA"
                Pattern locationPattern = Pattern.compile(
                        "(?i)(?:in|for|at)\\s+([A-Za-z\\s]+?)(?:\\?|$|\\.|,)");
                Matcher m = locationPattern.matcher(userRequest);
                if (m.find()) {
                    params.put("location", normalizeCity(m.group(1).trim()));
                } else {
                    // Try to find a known city code at the end
                    Pattern codePattern = Pattern.compile("(?i)\\b(SF|NYC|LA|CHI|SEA|MIA|DEN|ATL|BOS|DAL)\\b");
                    Matcher cm = codePattern.matcher(userRequest);
                    if (cm.find()) {
                        params.put("location", cm.group(1).toUpperCase());
                    }
                }
            }
            case "crm_query" -> {
                // Extract customer ID from patterns like:
                // "get customer C001", "lookup account C003", "find client C002"
                Pattern idPattern = Pattern.compile("(?i)\\b(C\\d{3})\\b");
                Matcher m = idPattern.matcher(userRequest);
                if (m.find()) {
                    params.put("customerId", m.group(1).toUpperCase());
                }
            }
            case "calculator_query" -> {
                // Extract math expression from patterns like:
                // "calculate 5 + 3", "compute 100 / 4", "what is 2 * 7"
                Pattern exprPattern = Pattern.compile("(\\d+\\.?\\d*\\s*[+\\-*/]\\s*\\d+\\.?\\d*)");
                Matcher m = exprPattern.matcher(userRequest);
                if (m.find()) {
                    params.put("expression", m.group(1).trim());
                }
            }
        }

        return params;
    }

    /** Normalize common city names to their airport-style codes. */
    private String normalizeCity(String city) {
        return switch (city.toLowerCase()) {
            case "san francisco", "sf" -> "SF";
            case "new york", "nyc", "new york city" -> "NYC";
            case "los angeles", "la" -> "LA";
            case "chicago", "chi" -> "CHI";
            case "seattle", "sea" -> "SEA";
            case "miami", "mia" -> "MIA";
            case "denver", "den" -> "DEN";
            case "atlanta", "atl" -> "ATL";
            case "boston", "bos" -> "BOS";
            case "dallas", "dal" -> "DAL";
            default -> city.toUpperCase();
        };
    }
}
