package dev.darshan.agentrouter.routing;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword/regex-based intent classifier.
 * Maps user requests to known intents using pattern matching.
 *
 * Intent mappings:
 * - "weather_query"    → weather, forecast, temperature, temp
 * - "crm_query"        → customer, account, contact, CRM, lookup
 * - "calculator_query" → calculate, compute, math, +, -, *, /
 */
@Component
public class KeywordClassifier implements IntentClassifier {

    /** Ordered list of intent patterns — first match wins. */
    private static final List<IntentPattern> PATTERNS = List.of(
            new IntentPattern("weather_query",
                    Pattern.compile("(?i).*(weather|forecast|temperature|temp\\b|climate).*")),
            new IntentPattern("crm_query",
                    Pattern.compile("(?i).*(customer|account|contact|crm|lookup|client).*")),
            new IntentPattern("calculator_query",
                    Pattern.compile("(?i).*(calculate|compute|math|\\d+\\s*[+\\-*/]\\s*\\d+).*"))
    );

    @Override
    public Optional<String> classify(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return Optional.empty();
        }

        return PATTERNS.stream()
                .filter(p -> p.pattern.matcher(userRequest).matches())
                .map(p -> p.intent)
                .findFirst();
    }

    /** Internal record pairing intent names with their regex patterns. */
    private record IntentPattern(String intent, Pattern pattern) {}
}
