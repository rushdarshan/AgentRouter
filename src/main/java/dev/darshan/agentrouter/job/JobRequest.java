package dev.darshan.agentrouter.job;

import java.util.Map;

public record JobRequest(String operationId, String workflow, Map<String, Object> parameters,
                         String principalId) {
}
