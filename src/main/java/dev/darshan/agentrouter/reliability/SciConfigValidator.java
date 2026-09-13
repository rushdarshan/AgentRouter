package dev.darshan.agentrouter.reliability;

import java.time.Duration;
import java.util.Objects;

public final class SciConfigValidator {
    private SciConfigValidator() {}

    public static void validate(SciConfig config) {
        Objects.requireNonNull(config, "config");
        if (!"sci-config-v1".equals(config.version())) fail("version");
        bounded(config.routerWorkers(), 1, 1024, "routerWorkers");
        bounded(config.perToolInFlight(), 1, 256, "perToolInFlight");
        bounded(config.routerQueue(), 1, 100_000, "routerQueue");
        bounded(config.workflowContainers(), 1, 256, "workflowContainers");
        bounded(config.workflowQueue(), 1, 100_000, "workflowQueue");
        bounded(config.controlCapacity(), 1, 1024, "controlCapacity");
        bounded(config.maxAttemptsPerInvocation(), 1, 3, "maxAttemptsPerInvocation");
        bounded(config.maxReconciliationLookups(), 1, 20, "maxReconciliationLookups");
        if (config.pollingInterval() == null || config.pollingInterval().compareTo(Duration.ofMillis(500)) < 0) {
            fail("pollingInterval");
        }
        if (!Duration.ofHours(168).equals(config.dedupRetention())
                || !Duration.ofHours(24).equals(config.retryWindow())
                || config.dedupRetention().compareTo(config.retryWindow()) < 0) {
            fail("retention windows");
        }
        if (!"sci-jwt".equals(config.authMechanism())
                || !"sci-c14n-v1".equals(config.canonicalizationProfile())) {
            fail("auth/canonicalization profile");
        }
    }

    private static void bounded(int value, int min, int max, String name) {
        if (value < min || value > max) fail(name);
    }

    private static void fail(String field) {
        throw new IllegalArgumentException("Invalid sci-config-v1 field: " + field);
    }
}
