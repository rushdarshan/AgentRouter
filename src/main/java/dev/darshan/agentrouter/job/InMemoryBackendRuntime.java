package dev.darshan.agentrouter.job;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic backend double for dispatch/recovery contract tests only. */
public final class InMemoryBackendRuntime implements BackendRuntime {
    private final ConcurrentHashMap<String, BackendObservation> containers = new ConcurrentHashMap<>();
    private final AtomicInteger executionCount = new AtomicInteger();

    @Override
    public BackendHandle createIfAbsent(String deterministicName, String operationId) {
        AtomicBoolean fresh = new AtomicBoolean(false);
        BackendObservation current = containers.computeIfAbsent(deterministicName, key -> {
            fresh.set(true);
            return new BackendObservation(key, operationId, true, false, "CREATED", 0);
        });
        if (!fresh.get() && !current.operationId().equals(operationId)) {
            throw new IllegalStateException("backend identity binding conflict");
        }
        return new BackendHandle(current.identity(), fresh.get());
    }

    public void markStarted(String name) {
        containers.computeIfPresent(name, (key, old) -> {
            if (!old.started()) executionCount.incrementAndGet();
            return new BackendObservation(old.identity(), old.operationId(), old.created(), true, "RUNNING", 0);
        });
    }

    @Override
    public void start(String backendIdentity) {
        markStarted(backendIdentity);
    }

    @Override
    public Optional<BackendObservation> inspect(String deterministicName) {
        return Optional.ofNullable(containers.get(deterministicName));
    }

    @Override
    public boolean cancel(String backendIdentity) {
        for (String name : containers.keySet()) {
            BackendObservation current = containers.get(name);
            if (current != null && current.identity().equals(backendIdentity)) {
                containers.put(name, new BackendObservation(current.identity(), current.operationId(),
                        current.created(), current.started(), "CANCELLED", 0));
                return true;
            }
        }
        return false;
    }

    public int size() {
        return containers.size();
    }

    public int executionCount() {
        return executionCount.get();
    }
}
