package dev.darshan.agentrouter.reliability;

import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        if (value == null || value.version() != 4 || value.variant() != 2) {
            throw new IllegalArgumentException("operationId must be a UUIDv4");
        }
    }

    public static OperationId parse(String text) {
        try {
            return new OperationId(UUID.fromString(text));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("operationId must be a UUIDv4");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
