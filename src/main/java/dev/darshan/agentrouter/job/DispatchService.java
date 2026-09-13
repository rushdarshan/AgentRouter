package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.Budget;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Identity-derived dispatch naming and no-delete/no-recreate safety policy. */
public final class DispatchService {
    private final BackendRuntime runtime;

    public DispatchService(BackendRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    /** Expired queued work never starts: zero executions, empty result. */
    public Optional<BackendRuntime.BackendHandle> dispatchIfEligible(String operationId, Budget budget) {
        if (budget != null && budget.expired()) {
            return Optional.empty();
        }
        return Optional.of(dispatch(operationId));
    }

    public BackendRuntime.BackendHandle dispatch(String operationId) {
        return dispatchIfOwned(operationId, () -> true);
    }

    /**
     * Dispatch refused without active-service ownership: no backend contact,
     * so unauthorized dispatch is absent, not merely unrecorded.
     */
    public BackendRuntime.BackendHandle dispatchIfOwned(String operationId, BooleanSupplier ownership) {
        if (!ownership.getAsBoolean()) {
            throw new IllegalStateException("refusing dispatch without active-service ownership");
        }
        String name = "sci-" + operationId;
        BackendRuntime.BackendHandle handle = runtime.createIfAbsent(name, operationId);
        if (handle.created()) runtime.start(handle.identity());
        runtime.inspect(name).ifPresent(observation -> {
            if (!operationId.equals(observation.operationId())) {
                throw new IllegalStateException("backend identity binding conflict");
            }
            if (observation.started() && !handle.created()) {
                // Existing execution is reconciled; it is never restarted.
            }
        });
        return handle;
    }

    public Optional<BackendRuntime.BackendObservation> inspect(String operationId) {
        return runtime.inspect("sci-" + operationId);
    }

    /** Live-backend cancel; caller must already hold control capacity. */
    public boolean cancel(String backendIdentity) {
        return runtime.cancel(Objects.requireNonNull(backendIdentity));
    }

    /**
     * Backend cancel through reserved control capacity. A full control queue
     * returns empty (explicit rejection, no false cancellation); an admitted
     * cancel always reaches the backend before the permit is released.
     */
    public Optional<Boolean> cancelIfAdmitted(String backendIdentity, ControlAdmission control) {
        Objects.requireNonNull(backendIdentity);
        if (control != null && !control.tryAcquireControl()) {
            return Optional.empty();
        }
        try {
            return Optional.of(runtime.cancel(backendIdentity));
        } finally {
            if (control != null) control.releaseControl();
        }
    }
}
