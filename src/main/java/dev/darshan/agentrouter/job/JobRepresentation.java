package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.*;

import java.time.Instant;

public record JobRepresentation(String operationId, String requestId, String principalRef,
                                 String idempotencyKey, String canonicalHash,
                                 OperationState operationState, Outcome outcome,
                                 EvidenceStatus evidenceStatus, RecoveryStatus recoveryStatus,
                                 ReasonCode reasonCode, boolean cancellationRequested,
                                 Instant acceptedAt, long stateVersion) {
}
