package dev.darshan.agentrouter.evidence;

/**
 * Core-tier Guard mapping: Router observations versus backend snapshot.
 * Pure function over frozen evidence; the independently-versioned Guard
 * implementation consumes the same frozen input at the integrated tier.
 * Runner rule: ERROR/REJECTED always fail the run; otherwise the verdict
 * must equal the scenario's expected verdict.
 *
 * <p>Router outcomes and backend states are deliberately different
 * vocabularies; consistency is a typed per-pair rule, never string equality.
 */
public final class GuardEvaluator {
    public enum GuardVerdict { PASS, FAIL, INSUFFICIENT, ERROR, REJECTED }

    /** Router outcome -> backend states that corroborate that typed assertion. */
    private static final java.util.Map<String, java.util.Set<String>> CONSISTENT =
            java.util.Map.of(
                    "SUCCESS", java.util.Set.of("COMPLETED"),
                    "EXECUTION_FAILURE", java.util.Set.of("FAILED"),
                    "CANCELLED", java.util.Set.of("CANCELLED"),
                    "UNKNOWN_OUTCOME", java.util.Set.of("UNKNOWN"),
                    "DEADLINE_EXCEEDED", java.util.Set.of("UNKNOWN", "FAILED"));

    public record GuardInput(boolean malformed, boolean crashed, String routerOutcome,
                             String backendState, boolean artifactsAvailable) {}

    public record GuardResult(GuardVerdict verdict, int runnerExit, boolean testOracleOnly) {
        public GuardResult(GuardVerdict verdict, int runnerExit) {
            this(verdict, runnerExit, true);
        }
    }

    public GuardResult evaluate(GuardInput in, GuardVerdict expected) {
        GuardVerdict actual;
        if (in.malformed()) {
            actual = GuardVerdict.REJECTED;
        } else if (in.crashed()) {
            actual = GuardVerdict.ERROR;
        } else if (!in.artifactsAvailable()) {
            actual = GuardVerdict.INSUFFICIENT;
        } else if (in.routerOutcome() == null || in.backendState() == null
                || !CONSISTENT.containsKey(in.routerOutcome())) {
            actual = GuardVerdict.REJECTED;
        } else if (CONSISTENT.get(in.routerOutcome()).contains(in.backendState())) {
            actual = GuardVerdict.PASS;
        } else {
            actual = GuardVerdict.FAIL;
        }
        boolean runnerFail = actual == GuardVerdict.ERROR || actual == GuardVerdict.REJECTED
                || actual != expected;
        return new GuardResult(actual, runnerFail ? 1 : 0);
    }
}
