package dev.darshan.agentrouter.job;

import java.util.Optional;

/**
 * Runtime boundary owned by the job service. Implementations may be Docker;
 * contract tests use an explicit in-memory implementation, never a fake
 * successful RooFit result.
 */
public interface BackendRuntime {
    BackendHandle createIfAbsent(String deterministicName, String operationId);
    /** Start only a newly created, proven-never-started backend unit. */
    default void start(String backendIdentity) {}
    Optional<BackendObservation> inspect(String deterministicName);
    boolean cancel(String backendIdentity);

    record BackendHandle(String identity, boolean created) {}
    record BackendObservation(String identity, String operationId, boolean created,
                             boolean started, String state, int exitCode) {}
}
