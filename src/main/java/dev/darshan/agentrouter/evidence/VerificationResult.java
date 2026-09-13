package dev.darshan.agentrouter.evidence;

import java.util.List;

public record VerificationResult(VerificationStatus status, List<String> diagnostics) {
    public boolean passed() { return status == VerificationStatus.PASS; }
}
