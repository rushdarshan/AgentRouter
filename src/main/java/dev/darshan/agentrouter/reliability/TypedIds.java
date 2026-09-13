package dev.darshan.agentrouter.reliability;

import java.util.UUID;

/** UUIDv4 identifiers with distinct types at API boundaries. */
public final class TypedIds {
    private TypedIds() {}

    public record RequestId(UUID value) {
        public RequestId { require(value, "requestId"); }
    }
    public record AttemptId(UUID value) {
        public AttemptId { require(value, "attemptId"); }
    }
    public record InvocationId(UUID value) {
        public InvocationId { require(value, "invocationId"); }
    }
    public record RunId(UUID value) {
        public RunId { require(value, "runId"); }
    }

    private static void require(UUID value, String name) {
        if (value == null || value.version() != 4 || value.variant() != 2) {
            throw new IllegalArgumentException(name + " must be a UUIDv4");
        }
    }
}
