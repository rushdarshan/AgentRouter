package dev.darshan.agentrouter.reliability;

import java.time.Duration;

/** Versioned, bounded execution policy shared by Router and job service. */
public record SciConfig(
        String version,
        int routerWorkers,
        int perToolInFlight,
        int routerQueue,
        int workflowContainers,
        int workflowQueue,
        int controlCapacity,
        int maxAttemptsPerInvocation,
        int maxReconciliationLookups,
        Duration pollingInterval,
        Duration dedupRetention,
        Duration retryWindow,
        String authMechanism,
        String canonicalizationProfile) {

    public static SciConfig defaults() {
        return new SciConfig("sci-config-v1", 16, 4, 64, 1, 4, 8, 3, 20,
                Duration.ofMillis(500), Duration.ofHours(168), Duration.ofHours(24),
                "sci-jwt", "sci-c14n-v1");
    }
}
