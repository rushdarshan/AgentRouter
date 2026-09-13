package dev.darshan.agentrouter.api;

import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.monitoring.ToolMetrics;
import dev.darshan.agentrouter.job.SqliteJobStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint exposing per-tool metrics and circuit states.
 *
 * GET /health — Returns tool health, latency percentiles, error rates
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final MetricsCollector metricsCollector;
    private final SqliteJobStore jobStore;

    public HealthController(MetricsCollector metricsCollector) {
        this(metricsCollector, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public HealthController(MetricsCollector metricsCollector, SqliteJobStore jobStore) {
        this.metricsCollector = metricsCollector;
        this.jobStore = jobStore;
    }

    /**
     * Return health status for all registered tools.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, ToolMetrics> allMetrics = metricsCollector.getAllMetrics();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> toolsMap = new LinkedHashMap<>();
        allMetrics.forEach((toolName, metrics) -> toolsMap.put(toolName, metrics.toMap()));
        response.put("tools", toolsMap);
        if (jobStore != null) {
            response.put("deployment", "single-instance-sqlite");
            response.put("active_service", jobStore.isOwner());
            response.put("queue_depth", jobStore.queueDepth());
            response.put("queue_capacity", jobStore.queueCapacity());
        }

        return ResponseEntity.ok(response);
    }
}
