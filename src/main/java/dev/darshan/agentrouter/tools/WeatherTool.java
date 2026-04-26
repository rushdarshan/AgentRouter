package dev.darshan.agentrouter.tools;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Simulated weather tool returning deterministic forecasts.
 * Supports US city codes: SF, NYC, LA, CHI, SEA, MIA, DEN, ATL, BOS, DAL.
 */
@Component
public class WeatherTool implements Tool {

    private static final Map<String, Map<String, Object>> WEATHER_DATA = Map.of(
        "SF",  Map.of("temp", 65, "forecast", "foggy",  "location", "San Francisco, CA"),
        "NYC", Map.of("temp", 72, "forecast", "sunny",  "location", "New York, NY"),
        "LA",  Map.of("temp", 82, "forecast", "clear",  "location", "Los Angeles, CA"),
        "CHI", Map.of("temp", 58, "forecast", "windy",  "location", "Chicago, IL"),
        "SEA", Map.of("temp", 55, "forecast", "rainy",  "location", "Seattle, WA"),
        "MIA", Map.of("temp", 88, "forecast", "humid",  "location", "Miami, FL"),
        "DEN", Map.of("temp", 62, "forecast", "partly cloudy", "location", "Denver, CO"),
        "ATL", Map.of("temp", 78, "forecast", "sunny",  "location", "Atlanta, GA"),
        "BOS", Map.of("temp", 60, "forecast", "cloudy", "location", "Boston, MA"),
        "DAL", Map.of("temp", 90, "forecast", "hot",    "location", "Dallas, TX")
    );

    @Override
    public String getName() {
        return "WeatherTool";
    }

    @Override
    public String getDescription() {
        return "Returns current weather conditions for US cities";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema()
                .addInputField("location", FieldType.STRING, true,
                        null, List.of("SF", "NYC", "LA", "CHI", "SEA", "MIA", "DEN", "ATL", "BOS", "DAL"))
                .addOutputField("temp", FieldType.NUMBER, true)
                .addOutputField("forecast", FieldType.STRING, true)
                .addOutputField("location", FieldType.STRING, true);
    }

    @Override
    public ToolCapability getCapability() {
        return ToolCapability.WEATHER;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String location = ((String) params.get("location")).toUpperCase();

        // Simulate network latency (20-80ms)
        simulateLatency();

        Map<String, Object> data = WEATHER_DATA.get(location);
        long elapsed = System.currentTimeMillis() - start;

        if (data == null) {
            return ToolResult.failure("Unknown location: " + location, elapsed);
        }

        return ToolResult.success(new HashMap<>(data), elapsed);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(20 + (long) (Math.random() * 60));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
